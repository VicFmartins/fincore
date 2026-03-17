package com.fincore.infrastructure.config;

import com.fincore.infrastructure.security.ApiKeyAccessDeniedHandler;
import com.fincore.infrastructure.security.ApiKeyAuthenticationEntryPoint;
import com.fincore.infrastructure.security.ApiKeyAuthenticationFilter;
import com.fincore.infrastructure.security.ApiKeySecurityProperties;
import com.fincore.infrastructure.security.WriteRateLimitingFilter;
import com.fincore.infrastructure.security.WriteRateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableConfigurationProperties({ApiKeySecurityProperties.class, WriteRateLimitProperties.class})
public class SecurityConfig {
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                                                   WriteRateLimitingFilter writeRateLimitingFilter,
                                                   ApiKeyAuthenticationEntryPoint authenticationEntryPoint,
                                                   ApiKeyAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(formLogin -> formLogin.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .logout(logout -> logout.disable())
            .requestCache(requestCache -> requestCache.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/accounts").hasAuthority("wallet:write")
                .requestMatchers(HttpMethod.POST, "/accounts/*/fund").hasAuthority("wallet:write")
                .requestMatchers(HttpMethod.POST, "/transactions/transfer").hasAuthority("wallet:write")
                .anyRequest().permitAll()
            )
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(writeRateLimitingFilter, ApiKeyAuthenticationFilter.class)
            .securityMatcher("/**");

        return http.build();
    }
}
