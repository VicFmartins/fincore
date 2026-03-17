package com.fincore.infrastructure;

import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.domain.account.Account;
import com.fincore.infrastructure.persistence.entity.LedgerEntryEntity;
import com.fincore.infrastructure.persistence.repository.AuditLogJpaRepository;
import com.fincore.infrastructure.persistence.repository.LedgerEntryJpaRepository;
import com.fincore.infrastructure.persistence.repository.TransactionJpaRepository;
import com.fincore.infrastructure.web.HttpHeaderNames;
import com.fincore.infrastructure.web.dto.AccountResponse;
import com.fincore.infrastructure.web.dto.CreateAccountRequest;
import com.fincore.infrastructure.web.dto.CreateAccountResponse;
import com.fincore.infrastructure.web.dto.ErrorResponse;
import com.fincore.infrastructure.web.dto.FundAccountRequest;
import com.fincore.infrastructure.web.dto.FundAccountResponse;
import com.fincore.infrastructure.web.dto.TransferRequest;
import com.fincore.infrastructure.web.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.task.scheduling.enabled=false",
        "fincore.outbox.scheduler.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "fincore.security.api-key.write-key=test-write-key",
        "fincore.security.api-key.read-only-key=test-read-key"
    }
)
@Testcontainers
class RestApiIntegrationTest {
    private static final String WRITE_API_KEY = "test-write-key";

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
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountRepositoryPort accountRepository;

    @Autowired
    private TransactionJpaRepository transactionRepository;

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryRepository;

    @Autowired
    private AuditLogJpaRepository auditLogRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs, processed_events, outbox, ledger_entries, transactions, accounts RESTART IDENTITY CASCADE");
    }

    @Test
    void create_account_returns_201_and_persists_account() {
        ResponseEntity<CreateAccountResponse> response = restTemplate.exchange(
            "/accounts",
            HttpMethod.POST,
            new HttpEntity<>(new CreateAccountRequest(), authorizedJsonHeaders(WRITE_API_KEY)),
            CreateAccountResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getFirst(HttpHeaderNames.CORRELATION_ID)).isNotBlank();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().balance()).isZero();
        assertThat(accountRepository.findById(response.getBody().id())).isPresent();
    }

    @Test
    void get_account_returns_200_for_existing_account() {
        Account account = accountRepository.save(new Account(UUID.randomUUID(), 450L));

        ResponseEntity<AccountResponse> response = restTemplate.getForEntity(
            "/accounts/" + account.getId(),
            AccountResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(account.getId());
        assertThat(response.getBody().balance()).isEqualTo(450L);
    }

    @Test
    void transfer_returns_200_and_preserves_idempotency() {
        Account source = accountRepository.save(new Account(UUID.randomUUID(), 900L));
        Account destination = accountRepository.save(new Account(UUID.randomUUID(), 100L));
        TransferRequest request = new TransferRequest(
            source.getId(),
            destination.getId(),
            250L,
            "api-idem-" + UUID.randomUUID()
        );

        ResponseEntity<TransferResponse> first = restTemplate.postForEntity(
            "/transactions/transfer",
            new HttpEntity<>(request, authorizedJsonHeaders(WRITE_API_KEY)),
            TransferResponse.class
        );
        ResponseEntity<TransferResponse> second = restTemplate.postForEntity(
            "/transactions/transfer",
            new HttpEntity<>(request, authorizedJsonHeaders(WRITE_API_KEY)),
            TransferResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().transactionId()).isEqualTo(first.getBody().transactionId());
        assertThat(second.getBody().status()).isEqualTo(first.getBody().status());
        assertThat(transactionRepository.count()).isEqualTo(1);

        Account updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        Account updatedDestination = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(updatedSource.getBalance()).isEqualTo(650L);
        assertThat(updatedDestination.getBalance()).isEqualTo(350L);
    }

    @Test
    void local_funding_returns_200_updates_balance_and_preserves_idempotency() {
        Account account = accountRepository.save(new Account(UUID.randomUUID(), 0L));
        FundAccountRequest request = new FundAccountRequest(500L, "fund-" + UUID.randomUUID());
        HttpHeaders headers = authorizedJsonHeaders(WRITE_API_KEY);
        headers.add(HttpHeaderNames.CORRELATION_ID, "corr-local-fund");

        ResponseEntity<FundAccountResponse> first = restTemplate.postForEntity(
            "/accounts/" + account.getId() + "/fund",
            new HttpEntity<>(request, headers),
            FundAccountResponse.class
        );
        ResponseEntity<FundAccountResponse> second = restTemplate.postForEntity(
            "/accounts/" + account.getId() + "/fund",
            new HttpEntity<>(request, headers),
            FundAccountResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();
        assertThat(first.getBody().accountId()).isEqualTo(account.getId());
        assertThat(first.getBody().balance()).isEqualTo(500L);
        assertThat(first.getBody().idempotentReplay()).isFalse();
        assertThat(second.getBody().transactionId()).isEqualTo(first.getBody().transactionId());
        assertThat(second.getBody().balance()).isEqualTo(500L);
        assertThat(second.getBody().idempotentReplay()).isTrue();

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualTo(500L);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(ledgerEntryRepository.findByTransactionId(first.getBody().transactionId()))
            .extracting(LedgerEntryEntity::getEntryType)
            .containsExactlyInAnyOrder("DEBIT", "CREDIT");

        assertThat(auditLogRepository.findAllByOrderByCreatedAtAsc())
            .filteredOn(log -> "LOCAL_FUND_SUCCEEDED".equals(log.getAction()))
            .singleElement()
            .satisfies(entity -> {
                assertThat(entity.getCorrelationId()).isEqualTo("corr-local-fund");
                assertThat(entity.getPayload()).contains(account.getId().toString());
            });
    }

    @Test
    void invalid_transfer_request_returns_standardized_400() {
        HttpHeaders headers = jsonHeaders();
        headers.add(HttpHeaderNames.CORRELATION_ID, "corr-invalid-transfer");
        headers.add(HttpHeaderNames.API_KEY, WRITE_API_KEY);
        HttpEntity<String> request = new HttpEntity<>(
            """
                {
                  "source_account_id": "%s",
                  "destination_account_id": "%s",
                  "amount": 0,
                  "idempotency_key": ""
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID()),
            headers
        );

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
            "/transactions/transfer",
            HttpMethod.POST,
            request,
            ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("bad_request");
        assertThat(response.getBody().path()).isEqualTo("/transactions/transfer");
        assertThat(response.getBody().correlationId()).isEqualTo("corr-invalid-transfer");
        assertThat(response.getBody().message()).contains("amount must be greater than 0");
        assertThat(response.getBody().message()).contains("idempotency_key is required");
    }

    @Test
    void get_account_returns_404_when_account_is_missing() {
        HttpHeaders headers = jsonHeaders();
        headers.add(HttpHeaderNames.CORRELATION_ID, "corr-missing-account");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
            "/accounts/" + UUID.randomUUID(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("not_found");
        assertThat(response.getBody().path()).startsWith("/accounts/");
        assertThat(response.getBody().correlationId()).isEqualTo("corr-missing-account");
        assertThat(response.getBody().message()).contains("account not found");
    }

    @Test
    void invalid_account_uuid_returns_standardized_400() {
        HttpHeaders headers = jsonHeaders();
        headers.add(HttpHeaderNames.CORRELATION_ID, "corr-invalid-uuid");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
            "/accounts/not-a-uuid",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("bad_request");
        assertThat(response.getBody().path()).isEqualTo("/accounts/not-a-uuid");
        assertThat(response.getBody().correlationId()).isEqualTo("corr-invalid-uuid");
        assertThat(response.getBody().message()).isEqualTo("invalid UUID value for parameter id");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authorizedJsonHeaders(String apiKey) {
        HttpHeaders headers = jsonHeaders();
        headers.add(HttpHeaderNames.API_KEY, apiKey);
        return headers;
    }
}
