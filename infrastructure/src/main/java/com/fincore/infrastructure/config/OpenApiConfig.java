package com.fincore.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.fincore.infrastructure.web.HttpHeaderNames.API_KEY;

@Configuration
public class OpenApiConfig {
    private static final String API_KEY_SCHEME = "apiKeyAuth";

    @Bean
    public OpenAPI fincoreOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("FinCore API")
                .description("High-consistency wallet and transfer API.")
                .version("v1")
                .contact(new Contact().name("FinCore")))
            .components(new Components().addSecuritySchemes(
                API_KEY_SCHEME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name(API_KEY)
                    .description("Required for protected write endpoints.")
            ));
    }
}
