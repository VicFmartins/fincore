package com.fincore.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.infrastructure.web.ApiErrorResponses;
import com.fincore.infrastructure.web.HttpHeaderNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class WriteRateLimitingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(WriteRateLimitingFilter.class);
    private static final String WRITE_AUTHORITY = "wallet:write";
    private static final Set<String> WRITE_ENDPOINTS = Set.of("/accounts", "/accounts/*/fund", "/transactions/transfer");

    private final WriteRateLimiter writeRateLimiter;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public WriteRateLimitingFilter(WriteRateLimiter writeRateLimiter, ObjectMapper objectMapper) {
        this.writeRateLimiter = writeRateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isRateLimitedWriteRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream().noneMatch(a -> WRITE_AUTHORITY.equals(a.getAuthority()))) {
            filterChain.doFilter(request, response);
            return;
        }

        String rateLimitKey = resolveRateLimitKey(request, authentication);
        WriteRateLimiter.RateLimitDecision decision = writeRateLimiter.allow(rateLimitKey);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1L, (long) Math.ceil(decision.retryAfterMillis() / 1000.0d));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));

        log.atWarn()
            .setMessage("write.request.rate_limited")
            .addKeyValue("path", request.getRequestURI())
            .addKeyValue("rate_limit_key", rateLimitKey)
            .addKeyValue("retry_after_seconds", retryAfterSeconds)
            .log();

        objectMapper.writeValue(
            response.getWriter(),
            ApiErrorResponses.build(HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded for write endpoints", request)
        );
    }

    private boolean isRateLimitedWriteRequest(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
            && WRITE_ENDPOINTS.stream().anyMatch(pattern -> pathMatcher.match(pattern, request.getRequestURI()));
    }

    private String resolveRateLimitKey(HttpServletRequest request, Authentication authentication) {
        String apiKey = request.getHeader(HttpHeaderNames.API_KEY);
        if (apiKey != null && !apiKey.isBlank()) {
            return "api-key:" + apiKey;
        }
        return "principal:" + authentication.getName();
    }
}
