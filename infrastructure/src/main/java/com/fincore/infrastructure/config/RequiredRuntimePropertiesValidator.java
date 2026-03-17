package com.fincore.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequiredRuntimePropertiesValidator {
    private static final List<String> REQUIRED_PROPERTIES = List.of(
        "spring.datasource.url",
        "spring.datasource.username",
        "spring.datasource.password",
        "spring.kafka.bootstrap-servers",
        "fincore.security.api-key.write-key",
        "fincore.security.api-key.read-only-key"
    );

    private final Environment environment;

    public RequiredRuntimePropertiesValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        for (String propertyName : REQUIRED_PROPERTIES) {
            String value = environment.getProperty(propertyName);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                    "Required configuration property is missing or blank: " + propertyName
                );
            }
        }
    }
}
