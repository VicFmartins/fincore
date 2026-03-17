package com.fincore.infrastructure.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
public class KafkaConnectivityHealthConfiguration {
    @Bean("kafkaConnectivity")
    public HealthIndicator kafkaConnectivityHealthIndicator(KafkaAdmin kafkaAdmin) {
        return () -> {
            Map<String, Object> configuration = kafkaAdmin.getConfigurationProperties();
            try (AdminClient adminClient = AdminClient.create(configuration)) {
                DescribeClusterResult cluster = adminClient.describeCluster();
                int nodeCount = cluster.nodes().get(3, TimeUnit.SECONDS).size();
                String clusterId = cluster.clusterId().get(3, TimeUnit.SECONDS);
                return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodeCount", nodeCount)
                    .build();
            } catch (Exception ex) {
                return Health.down(ex).build();
            }
        };
    }
}
