package com.fincore.infrastructure.config;

import com.fincore.infrastructure.kafka.TransactionCompletedConsumerHook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsumerSupportConfig {
    @Bean
    @ConditionalOnMissingBean(TransactionCompletedConsumerHook.class)
    public TransactionCompletedConsumerHook defaultTransactionCompletedConsumerHook() {
        return eventId -> {
        };
    }
}
