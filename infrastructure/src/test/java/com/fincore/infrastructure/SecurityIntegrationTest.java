package com.fincore.infrastructure;

import com.fincore.application.ports.AccountRepositoryPort;
import com.fincore.domain.account.Account;
import com.fincore.infrastructure.security.WriteRateLimiter;
import com.fincore.infrastructure.web.HttpHeaderNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.task.scheduling.enabled=false",
        "fincore.outbox.scheduler.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "fincore.security.rate-limit.write.max-requests=2",
        "fincore.security.rate-limit.write.window=PT1M",
        "fincore.security.api-key.write-key=test-write-key",
        "fincore.security.api-key.read-only-key=test-read-key"
    }
)
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest {
    private static final String WRITE_API_KEY = "test-write-key";
    private static final String READ_ONLY_API_KEY = "test-read-key";

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
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WriteRateLimiter writeRateLimiter;

    @Autowired
    private AccountRepositoryPort accountRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs, processed_events, outbox, ledger_entries, transactions, accounts RESTART IDENTITY CASCADE");
        writeRateLimiter.reset();
    }

    @Test
    void health_endpoint_is_public() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openapi_endpoints_are_public() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/accounts']").exists())
            .andExpect(jsonPath("$.paths['/transactions/transfer']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.apiKeyAuth.type").value("apiKey"))
            .andExpect(jsonPath("$.paths['/accounts'].post.security").isArray())
            .andExpect(jsonPath("$.paths['/transactions/transfer'].post.security").isArray());

        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void readiness_and_liveness_probes_are_public_and_up() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protected_write_endpoint_returns_401_without_auth() throws Exception {
        mockMvc.perform(post("/accounts")
                .contentType(APPLICATION_JSON)
                .header(HttpHeaderNames.CORRELATION_ID, "corr-no-auth"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaderNames.CORRELATION_ID, "corr-no-auth"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("unauthorized"))
            .andExpect(jsonPath("$.message").value("authentication is required"))
            .andExpect(jsonPath("$.correlation_id").value("corr-no-auth"));
    }

    @Test
    void protected_write_endpoint_allows_valid_api_key() throws Exception {
        mockMvc.perform(post("/accounts")
                .contentType(APPLICATION_JSON)
                .content("{}")
                .header(HttpHeaderNames.API_KEY, WRITE_API_KEY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.balance").value(0))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void write_requests_within_rate_limit_are_allowed() throws Exception {
        mockMvc.perform(post("/accounts")
                .contentType(APPLICATION_JSON)
                .content("{}")
                .header(HttpHeaderNames.API_KEY, WRITE_API_KEY))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts")
                .contentType(APPLICATION_JSON)
                .content("{}")
                .header(HttpHeaderNames.API_KEY, WRITE_API_KEY))
            .andExpect(status().isCreated());
    }

    @Test
    void write_requests_over_rate_limit_return_429() throws Exception {
        mockMvc.perform(post("/accounts")
                .contentType(APPLICATION_JSON)
                .content("{}")
                .header(HttpHeaderNames.API_KEY, WRITE_API_KEY))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts")
                .contentType(APPLICATION_JSON)
                .content("{}")
                .header(HttpHeaderNames.API_KEY, WRITE_API_KEY))
            .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/accounts")
                .contentType(APPLICATION_JSON)
                .content("{}")
                .header(HttpHeaderNames.API_KEY, WRITE_API_KEY)
                .header(HttpHeaderNames.CORRELATION_ID, "corr-rate-limit"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string(HttpHeaderNames.CORRELATION_ID, "corr-rate-limit"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.error").value("too_many_requests"))
            .andExpect(jsonPath("$.message").value("rate limit exceeded for write endpoints"))
            .andExpect(jsonPath("$.correlation_id").value("corr-rate-limit"))
            .andReturn();

        String retryAfterHeader = result.getResponse().getHeader("Retry-After");
        assertThat(retryAfterHeader).isNotBlank();
        assertThat(Long.parseLong(retryAfterHeader)).isPositive();
    }

    @Test
    void local_funding_endpoint_requires_write_authority() throws Exception {
        Account account = accountRepository.save(new Account(UUID.randomUUID(), 0L));

        mockMvc.perform(post("/accounts/" + account.getId() + "/fund")
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":100,\"idempotency_key\":\"fund-security\"}")
                .header(HttpHeaderNames.API_KEY, READ_ONLY_API_KEY)
                .header(HttpHeaderNames.CORRELATION_ID, "corr-fund-forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.correlation_id").value("corr-fund-forbidden"));

        mockMvc.perform(post("/accounts/" + account.getId() + "/fund")
                .contentType(APPLICATION_JSON)
                .content("{\"amount\":100,\"idempotency_key\":\"fund-security\"}")
                .header(HttpHeaderNames.API_KEY, WRITE_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(100))
            .andExpect(jsonPath("$.idempotent_replay").value(false));
    }

    @Test
    void invalid_api_key_is_rejected_with_401() throws Exception {
        mockMvc.perform(post("/accounts")
                .contentType(APPLICATION_JSON)
                .header(HttpHeaderNames.API_KEY, "bad-key")
                .header(HttpHeaderNames.CORRELATION_ID, "corr-invalid-auth"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaderNames.CORRELATION_ID, "corr-invalid-auth"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("unauthorized"))
            .andExpect(jsonPath("$.message").value("invalid API key"))
            .andExpect(jsonPath("$.correlation_id").value("corr-invalid-auth"));
    }

    @Test
    void read_only_api_key_is_forbidden_on_write_endpoint() throws Exception {
        mockMvc.perform(post("/accounts")
                .contentType(APPLICATION_JSON)
                .header(HttpHeaderNames.API_KEY, READ_ONLY_API_KEY)
                .header(HttpHeaderNames.CORRELATION_ID, "corr-forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(header().string(HttpHeaderNames.CORRELATION_ID, "corr-forbidden"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("forbidden"))
            .andExpect(jsonPath("$.message").value("access is forbidden"))
            .andExpect(jsonPath("$.correlation_id").value("corr-forbidden"));
    }
}
