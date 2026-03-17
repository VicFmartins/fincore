package com.fincore.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
class OperationalHealthIntegrationTest {
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
    private TestRestTemplate restTemplate;

    @Test
    void health_endpoints_are_exposed_and_up() {
        ResponseEntity<String> general = restTemplate.getForEntity("/actuator/health", String.class);
        ResponseEntity<String> readiness = restTemplate.getForEntity("/actuator/health/readiness", String.class);
        ResponseEntity<String> liveness = restTemplate.getForEntity("/actuator/health/liveness", String.class);

        assertThat(general.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(general.getBody()).contains("\"status\":\"UP\"");
        assertThat(readiness.getBody()).contains("\"status\":\"UP\"");
        assertThat(liveness.getBody()).contains("\"status\":\"UP\"");
    }
}
