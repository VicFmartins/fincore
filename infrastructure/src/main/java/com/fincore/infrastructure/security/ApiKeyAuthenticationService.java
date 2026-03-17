package com.fincore.infrastructure.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ApiKeyAuthenticationService {
    private static final SimpleGrantedAuthority WRITE_AUTHORITY = new SimpleGrantedAuthority("wallet:write");
    private static final SimpleGrantedAuthority READ_AUTHORITY = new SimpleGrantedAuthority("wallet:read");

    private final Map<String, Authentication> authenticationsByKey;

    public ApiKeyAuthenticationService(ApiKeySecurityProperties properties) {
        Map<String, Authentication> authentications = new HashMap<>();
        register(authentications, properties.getWriteKey(), "fincore-writer", List.of(WRITE_AUTHORITY, READ_AUTHORITY));
        register(authentications, properties.getReadOnlyKey(), "fincore-reader", List.of(READ_AUTHORITY));
        this.authenticationsByKey = Map.copyOf(authentications);
    }

    public Optional<Authentication> authenticate(String apiKey) {
        return Optional.ofNullable(authenticationsByKey.get(apiKey));
    }

    private void register(Map<String, Authentication> authentications,
                          String apiKey,
                          String principal,
                          List<SimpleGrantedAuthority> authorities) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        authentications.put(
            apiKey,
            UsernamePasswordAuthenticationToken.authenticated(principal, apiKey, authorities)
        );
    }
}
