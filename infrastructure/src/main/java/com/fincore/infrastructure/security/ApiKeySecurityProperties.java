package com.fincore.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fincore.security.api-key")
public class ApiKeySecurityProperties {
    @NotBlank
    private String writeKey;
    @NotBlank
    private String readOnlyKey;

    public String getWriteKey() {
        return writeKey;
    }

    public void setWriteKey(String writeKey) {
        this.writeKey = writeKey;
    }

    public String getReadOnlyKey() {
        return readOnlyKey;
    }

    public void setReadOnlyKey(String readOnlyKey) {
        this.readOnlyKey = readOnlyKey;
    }
}
