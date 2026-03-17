package com.fincore.infrastructure;

import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.application.usecases.BusinessRuleViolationException;
import com.fincore.application.usecases.NotFoundException;
import com.fincore.application.usecases.TransferCommand;
import com.fincore.application.usecases.TransferResult;
import com.fincore.domain.account.Account;
import com.fincore.infrastructure.kafka.OutboxPublisher;
import com.fincore.infrastructure.observability.CorrelationContext;
import com.fincore.infrastructure.observability.FincoreMetrics;
import com.fincore.infrastructure.persistence.entity.AuditLogEntity;
import com.fincore.infrastructure.persistence.entity.LedgerEntryEntity;
import com.fincore.infrastructure.persistence.repository.AuditLogJpaRepository;
import com.fincore.infrastructure.persistence.repository.LedgerEntryJpaRepository;
import com.fincore.infrastructure.persistence.repository.OutboxJpaRepository;
import com.fincore.infrastructure.persistence.repository.TransactionJpaRepository;
import com.fincore.infrastructure.service.TransferService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.task.scheduling.enabled=false",
    "fincore.outbox.scheduler.enabled=false",
    "fincore.security.api-key.write-key=test-write-key",
    "fincore.security.api-key.read-only-key=test-read-key"
})
@Testcontainers
class TransferIntegrationTest {
    private static final AtomicBoolean FAIL_NEXT_SEND = new AtomicBoolean(false);
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13.10-alpine")
        .withDatabaseName("fincore")
        .withUsername("fincore")
        .withPassword("fincore");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepositoryPort accountRepository;

    @Autowired
    private TransactionJpaRepository transactionRepository;

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryRepository;

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Autowired
    private AuditLogJpaRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs, processed_events, outbox, ledger_entries, transactions, accounts RESTART IDENTITY CASCADE");
        FAIL_NEXT_SEND.set(false);
    }

    @Test
    void idempotency_prevents_duplicate_transactions() {
        Account source = createAccount(1_000L);
        Account destination = createAccount(0L);

        TransferCommand command = new TransferCommand(
            source.getId(),
            destination.getId(),
            200L,
            "idem-1"
        );

        TransferResult first = transferService.transfer(command);
        TransferResult second = transferService.transfer(command);

        assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(second.getStatus()).isEqualTo(first.getStatus());
        assertThat(first.isIdempotentReplay()).isFalse();
        assertThat(second.isIdempotentReplay()).isTrue();
        assertThat(transactionRepository.count()).isEqualTo(1);

        List<LedgerEntryEntity> entries = ledgerEntryRepository.findByTransactionId(first.getTransactionId());
        assertThat(entries).hasSize(2);

        Account updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        Account updatedDestination = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(updatedSource.getBalance()).isEqualTo(800L);
        assertThat(updatedDestination.getBalance()).isEqualTo(200L);
    }

    @Test
    void concurrent_transfers_use_pessimistic_locking() throws Exception {
        Account source = createAccount(100L);
        Account destinationA = createAccount(0L);
        Account destinationB = createAccount(0L);

        TransferCommand first = new TransferCommand(
            source.getId(),
            destinationA.getId(),
            80L,
            "idem-a"
        );
        TransferCommand second = new TransferCommand(
            source.getId(),
            destinationB.getId(),
            80L,
            "idem-b"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<TransferAttempt>> tasks = List.of(
            () -> attemptTransfer(first, ready, start),
            () -> attemptTransfer(second, ready, start)
        );

        List<Future<TransferAttempt>> futures = new ArrayList<>();
        for (Callable<TransferAttempt> task : tasks) {
            futures.add(executor.submit(task));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<TransferAttempt> attempts = new ArrayList<>();
        for (Future<TransferAttempt> future : futures) {
            attempts.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        long successCount = attempts.stream().filter(attempt -> attempt.result() != null).count();
        long failureCount = attempts.stream().filter(attempt -> attempt.error() != null).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);
        assertThat(attempts.stream()
            .filter(attempt -> attempt.error() != null)
            .findFirst()
            .orElseThrow()
            .error()).isInstanceOf(BusinessRuleViolationException.class);

        Account updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        assertThat(updatedSource.getBalance()).isEqualTo(20L);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(ledgerEntryRepository.count()).isEqualTo(2);
    }

    @Test
    void ledger_contains_debit_and_credit_entries() {
        Account source = createAccount(500L);
        Account destination = createAccount(0L);

        TransferResult result = transferService.transfer(new TransferCommand(
            source.getId(),
            destination.getId(),
            150L,
            "idem-ledger"
        ));

        List<LedgerEntryEntity> entries = ledgerEntryRepository.findByTransactionId(result.getTransactionId());
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().map(LedgerEntryEntity::getEntryType).toList())
            .containsExactlyInAnyOrder("DEBIT", "CREDIT");
    }

    @Test
    void outbox_event_published_after_transaction_commit() {
        Account source = createAccount(1_000L);
        Account destination = createAccount(0L);
        double publishedBefore = counterValue(FincoreMetrics.OUTBOX_PUBLISHED);

        TransferResult result = transferService.transfer(new TransferCommand(
            source.getId(),
            destination.getId(),
            100L,
            "idem-outbox-1"
        ));

        try (KafkaConsumer<String, String> consumer = createConsumer("transaction.completed")) {
            outboxPublisher.publishPending(10);
            String txId = result.getTransactionId().toString();
            List<ConsumerRecord<String, String>> records = pollRecords(
                consumer,
                record -> record.value() != null && record.value().contains(txId),
                1,
                Duration.ofSeconds(5)
            );

            assertThat(records).hasSize(1);
            assertThat(records.get(0).value()).contains(result.getTransactionId().toString());
            assertThat(outboxRepository.count()).isEqualTo(2);
            assertThat(outboxRepository.findAll().stream().allMatch(e -> e.getPublishedAt() != null)).isTrue();
            assertThat(counterValue(FincoreMetrics.OUTBOX_PUBLISHED) - publishedBefore).isEqualTo(2.0d);
        }
    }

    @Test
    void transfer_metrics_split_request_success_from_unique_execution_success() {
        Account source = createAccount(1_000L);
        Account destination = createAccount(0L);
        double requestSuccessBefore = counterValue(FincoreMetrics.TRANSFER_REQUESTS_SUCCESS);
        double executionSuccessBefore = counterValue(FincoreMetrics.TRANSFER_EXECUTIONS_SUCCESS);

        TransferCommand command = new TransferCommand(
            source.getId(),
            destination.getId(),
            200L,
            "idem-metrics-success"
        );

        TransferResult first = transferService.transfer(command);
        TransferResult second = transferService.transfer(command);

        assertThat(first.isIdempotentReplay()).isFalse();
        assertThat(second.isIdempotentReplay()).isTrue();
        assertThat(counterValue(FincoreMetrics.TRANSFER_REQUESTS_SUCCESS) - requestSuccessBefore).isEqualTo(2.0d);
        assertThat(counterValue(FincoreMetrics.TRANSFER_EXECUTIONS_SUCCESS) - executionSuccessBefore).isEqualTo(1.0d);
    }

    @Test
    void failed_transfer_increments_request_failure_metric() {
        Account source = createAccount(50L);
        Account destination = createAccount(0L);
        double failuresBefore = counterValue(FincoreMetrics.TRANSFER_REQUESTS_FAILURE);

        assertThatThrownBy(() -> transferService.transfer(new TransferCommand(
            source.getId(),
            destination.getId(),
            100L,
            "idem-metrics-failure"
        )))
            .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(counterValue(FincoreMetrics.TRANSFER_REQUESTS_FAILURE) - failuresBefore).isEqualTo(1.0d);
    }

    @Test
    void failed_publish_retries_later() {
        Account source = createAccount(1_000L);
        Account destination = createAccount(0L);
        double publishedBefore = counterValue(FincoreMetrics.OUTBOX_PUBLISHED);
        double failuresBefore = counterValue(FincoreMetrics.OUTBOX_PUBLISH_FAILURES);

        TransferResult result = transferService.transfer(new TransferCommand(
            source.getId(),
            destination.getId(),
            120L,
            "idem-outbox-2"
        ));

        FAIL_NEXT_SEND.set(true);
        assertThatThrownBy(() -> outboxPublisher.publishPending(10))
            .isInstanceOf(RuntimeException.class);

        try (KafkaConsumer<String, String> consumer = createConsumer("transaction.completed")) {
            outboxPublisher.publishPending(10);
            String txId = result.getTransactionId().toString();
            List<ConsumerRecord<String, String>> records = pollRecords(
                consumer,
                record -> record.value() != null && record.value().contains(txId),
                1,
                Duration.ofSeconds(5)
            );

            assertThat(records).hasSize(1);
            assertThat(outboxRepository.findAll().stream().allMatch(e -> e.getPublishedAt() != null)).isTrue();
            assertThat(counterValue(FincoreMetrics.OUTBOX_PUBLISHED) - publishedBefore).isEqualTo(2.0d);
            assertThat(counterValue(FincoreMetrics.OUTBOX_PUBLISH_FAILURES) - failuresBefore).isEqualTo(1.0d);
        }
    }

    @Test
    void publisher_does_not_duplicate_events() {
        Account source = createAccount(1_000L);
        Account destination = createAccount(0L);

        TransferResult result = transferService.transfer(new TransferCommand(
            source.getId(),
            destination.getId(),
            75L,
            "idem-outbox-3"
        ));

        try (KafkaConsumer<String, String> consumer = createConsumer("transaction.completed")) {
            outboxPublisher.publishPending(10);
            outboxPublisher.publishPending(10);

            String txId = result.getTransactionId().toString();
            List<ConsumerRecord<String, String>> records = pollRecords(
                consumer,
                record -> record.value() != null && record.value().contains(txId),
                1,
                Duration.ofSeconds(5)
            );
            assertThat(records).hasSize(1);
        }
    }

    @Test
    void atomicity_rolls_back_when_destination_missing() {
        Account source = createAccount(400L);
        UUID missingDestination = UUID.randomUUID();

        TransferCommand command = new TransferCommand(
            source.getId(),
            missingDestination,
            100L,
            "idem-missing"
        );

        assertThatThrownBy(() -> transferService.transfer(command))
            .isInstanceOf(NotFoundException.class);

        assertThat(transactionRepository.count()).isZero();
        assertThat(ledgerEntryRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();

        Account reloaded = accountRepository.findById(source.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualTo(400L);
    }

    @Test
    void audit_log_is_created_for_successful_transfer() {
        Account source = createAccount(1_000L);
        Account destination = createAccount(0L);
        String correlationId = "corr-transfer-success";

        TransferResult result;
        try (CorrelationContext.Scope ignored = CorrelationContext.openScope(correlationId)) {
            result = transferService.transfer(new TransferCommand(
                source.getId(),
                destination.getId(),
                125L,
                "audit-success"
            ));
        }

        List<AuditLogEntity> auditLogs = auditLogRepository.findAllByOrderByCreatedAtAsc();

        assertThat(auditLogs).hasSize(2);
        assertThat(auditLogs.stream().map(AuditLogEntity::getAction).toList())
            .containsExactly("TRANSFER_REQUESTED", "TRANSFER_SUCCEEDED");
        assertThat(auditLogs.stream().map(AuditLogEntity::getCorrelationId).toList())
            .containsOnly(correlationId);

        AuditLogEntity successLog = auditLogs.stream()
            .filter(log -> "TRANSFER_SUCCEEDED".equals(log.getAction()))
            .findFirst()
            .orElseThrow();
        assertThat(successLog.getEntityType()).isEqualTo("TRANSACTION");
        assertThat(successLog.getEntityId()).isEqualTo(result.getTransactionId().toString());
        assertThat(successLog.getPayload()).contains(result.getTransactionId().toString());
    }

    @Test
    void audit_log_is_created_for_failed_transfer_with_correlation_id() {
        Account source = createAccount(50L);
        Account destination = createAccount(0L);
        String correlationId = "corr-transfer-failure";

        try (CorrelationContext.Scope ignored = CorrelationContext.openScope(correlationId)) {
            assertThatThrownBy(() -> transferService.transfer(new TransferCommand(
                source.getId(),
                destination.getId(),
                100L,
                "audit-failure"
            )))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("insufficient funds");
        }

        List<AuditLogEntity> auditLogs = auditLogRepository.findAllByOrderByCreatedAtAsc();

        assertThat(auditLogs).hasSize(2);
        assertThat(auditLogs.stream().map(AuditLogEntity::getAction).toList())
            .containsExactly("TRANSFER_REQUESTED", "TRANSFER_FAILED");
        assertThat(auditLogs.stream().map(AuditLogEntity::getCorrelationId).toList())
            .containsOnly(correlationId);

        AuditLogEntity failureLog = auditLogs.stream()
            .filter(log -> "TRANSFER_FAILED".equals(log.getAction()))
            .findFirst()
            .orElseThrow();
        assertThat(failureLog.getEntityType()).isEqualTo("TRANSFER_REQUEST");
        assertThat(failureLog.getEntityId()).isEqualTo("audit-failure");
        assertThat(failureLog.getPayload()).contains("insufficient funds");
    }

    private Account createAccount(long balance) {
        return accountRepository.save(new Account(UUID.randomUUID(), balance));
    }

    private TransferAttempt attemptTransfer(TransferCommand command,
                                            CountDownLatch ready,
                                            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out waiting to start transfer");
        }
        try {
            return new TransferAttempt(transferService.transfer(command), null);
        } catch (Exception ex) {
            return new TransferAttempt(null, ex);
        }
    }

    private record TransferAttempt(TransferResult result, Exception error) {
    }

    private KafkaConsumer<String, String> createConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private List<ConsumerRecord<String, String>> pollRecords(KafkaConsumer<String, String> consumer,
                                                             java.util.function.Predicate<ConsumerRecord<String, String>> filter,
                                                             int expected,
                                                             Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        while (System.nanoTime() < deadline && records.size() < expected) {
            ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(250));
            polled.forEach(record -> {
                if (filter.test(record)) {
                    records.add(record);
                }
            });
        }
        return records;
    }

    @TestConfiguration
    static class KafkaFailureConfig {
        @Bean
        @Primary
        KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
            return new FailingKafkaTemplate(producerFactory, FAIL_NEXT_SEND);
        }
    }

    static class FailingKafkaTemplate extends KafkaTemplate<String, String> {
        private final AtomicBoolean failNext;

        FailingKafkaTemplate(ProducerFactory<String, String> producerFactory, AtomicBoolean failNext) {
            super(producerFactory);
            this.failNext = failNext;
        }

        @Override
        public CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> send(ProducerRecord<String, String> record) {
            if (failNext.getAndSet(false)) {
                CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failed = new CompletableFuture<>();
                failed.completeExceptionally(new RuntimeException("simulated kafka failure"));
                return failed;
            }
            return super.send(record);
        }
    }

    private double counterValue(String counterName) {
        return meterRegistry.get(counterName).counter().count();
    }
}
