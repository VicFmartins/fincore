package com.fincore.infrastructure;

import com.fincore.domain.account.Account;
import com.fincore.infrastructure.kafka.KafkaHeaderNames;
import com.fincore.infrastructure.kafka.TransactionCompletedConsumer;
import com.fincore.infrastructure.kafka.TransactionCompletedConsumerHook;
import com.fincore.infrastructure.observability.FincoreMetrics;
import com.fincore.infrastructure.persistence.entity.OutboxEntity;
import com.fincore.infrastructure.persistence.repository.OutboxJpaRepository;
import com.fincore.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import com.fincore.infrastructure.service.TransferService;
import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.application.usecases.TransferCommand;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.task.scheduling.enabled=false",
    "fincore.outbox.scheduler.enabled=false",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.listener.auto-startup=false",
    "fincore.security.api-key.write-key=test-write-key",
    "fincore.security.api-key.read-only-key=test-read-key"
})
@Testcontainers
class TransactionCompletedConsumerIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13.10-alpine")
        .withDatabaseName("fincore")
        .withUsername("fincore")
        .withPassword("fincore");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepositoryPort accountRepository;

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailureInjectingHook hook;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TransactionCompletedConsumer transactionCompletedConsumer;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs, processed_events, outbox, ledger_entries, transactions, accounts RESTART IDENTITY CASCADE");
        hook.reset();
    }

    @Test
    void same_event_consumed_twice_is_processed_once() {
        OutboxEntity completedEvent = createCompletedEvent();
        double processedBefore = counterValue(FincoreMetrics.CONSUMER_PROCESSED);
        double deduplicatedBefore = counterValue(FincoreMetrics.CONSUMER_DEDUPLICATED);

        consumeCompletedEvent(completedEvent);
        awaitCondition(() -> processedEventRepository.count() == 1, Duration.ofSeconds(5));

        consumeCompletedEvent(completedEvent);
        awaitCondition(
            () -> counterValue(FincoreMetrics.CONSUMER_DEDUPLICATED) - deduplicatedBefore == 1.0d,
            Duration.ofSeconds(5)
        );

        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.findById(completedEvent.getId())).isPresent();
        assertThat(counterValue(FincoreMetrics.CONSUMER_PROCESSED) - processedBefore).isEqualTo(1.0d);
        assertThat(counterValue(FincoreMetrics.CONSUMER_DEDUPLICATED) - deduplicatedBefore).isEqualTo(1.0d);
    }

    @Test
    void retry_after_post_commit_failure_does_not_duplicate_processing() {
        OutboxEntity completedEvent = createCompletedEvent();
        double processedBefore = counterValue(FincoreMetrics.CONSUMER_PROCESSED);
        double deduplicatedBefore = counterValue(FincoreMetrics.CONSUMER_DEDUPLICATED);
        hook.failOnceAfter(completedEvent.getId());

        assertThatThrownBy(() -> consumeCompletedEvent(completedEvent))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("simulated listener failure after commit");
        awaitCondition(() -> processedEventRepository.findById(completedEvent.getId()).isPresent(), Duration.ofSeconds(5));
        assertThat(hook.failuresFor(completedEvent.getId())).isEqualTo(1);

        consumeCompletedEvent(completedEvent);
        awaitCondition(
            () -> counterValue(FincoreMetrics.CONSUMER_DEDUPLICATED) - deduplicatedBefore == 1.0d,
            Duration.ofSeconds(5)
        );

        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.findById(completedEvent.getId())).isPresent();
        assertThat(counterValue(FincoreMetrics.CONSUMER_PROCESSED) - processedBefore).isEqualTo(1.0d);
        assertThat(counterValue(FincoreMetrics.CONSUMER_DEDUPLICATED) - deduplicatedBefore).isEqualTo(1.0d);
    }

    private OutboxEntity createCompletedEvent() {
        Account source = accountRepository.save(new Account(UUID.randomUUID(), 1_000L));
        Account destination = accountRepository.save(new Account(UUID.randomUUID(), 0L));

        UUID transactionId = transferService.transfer(new TransferCommand(
            source.getId(),
            destination.getId(),
            125L,
            "consumer-" + UUID.randomUUID()
        )).getTransactionId();

        return outboxRepository.findAll().stream()
            .filter(event -> "transaction.completed".equals(event.getEventType()))
            .filter(event -> transactionId.equals(event.getAggregateId()))
            .findFirst()
            .orElseThrow();
    }

    private void consumeCompletedEvent(OutboxEntity completedEvent) {
        Map<String, Object> headers = new ConcurrentHashMap<>();
        headers.put(KafkaHeaderNames.EVENT_ID, completedEvent.getId().toString().getBytes());
        headers.put(KafkaHeaderNames.AGGREGATE_ID, completedEvent.getAggregateId().toString().getBytes());
        headers.put(KafkaHeaderNames.AGGREGATE_TYPE, completedEvent.getAggregateType().getBytes());
        if (completedEvent.getCorrelationId() != null) {
            headers.put(KafkaHeaderNames.CORRELATION_ID, completedEvent.getCorrelationId().getBytes());
        }
        try {
            TransactionCompletedConsumer target = AopTestUtils.getTargetObject(transactionCompletedConsumer);
            target.consume(completedEvent.getPayload(), headers);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("failed to invoke transaction completed consumer", ex);
        }
    }

    private void awaitCondition(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for condition", ex);
            }
        }
        throw new AssertionError("condition was not met within " + timeout);
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    @TestConfiguration
    static class ConsumerHookConfig {
        @Bean
        @Primary
        FailureInjectingHook transactionCompletedConsumerHook() {
            return new FailureInjectingHook();
        }
    }

    static class FailureInjectingHook implements TransactionCompletedConsumerHook {
        private final ConcurrentMap<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final ConcurrentMap<UUID, AtomicInteger> failures = new ConcurrentHashMap<>();
        private final ConcurrentMap<UUID, Boolean> failAfterSuccess = new ConcurrentHashMap<>();

        @Override
        public void afterHandling(UUID eventId) {
            attempts.computeIfAbsent(eventId, ignored -> new AtomicInteger()).incrementAndGet();
            if (Boolean.TRUE.equals(failAfterSuccess.remove(eventId))) {
                failures.computeIfAbsent(eventId, ignored -> new AtomicInteger()).incrementAndGet();
                throw new RuntimeException("simulated listener failure after commit");
            }
        }

        void failOnceAfter(UUID eventId) {
            failAfterSuccess.put(eventId, true);
        }

        int attemptsFor(UUID eventId) {
            return attempts.getOrDefault(eventId, new AtomicInteger()).get();
        }

        int failuresFor(UUID eventId) {
            return failures.getOrDefault(eventId, new AtomicInteger()).get();
        }

        void reset() {
            attempts.clear();
            failures.clear();
            failAfterSuccess.clear();
        }
    }

    private double counterValue(String counterName) {
        return meterRegistry.get(counterName).counter().count();
    }
}
