package com.codefactory.bookingplatform.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "supabaseJwt";

    @Bean
    public OpenAPI bookingPlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Booking Platform API")
                        .version("v1")
                        .description("Service booking platform - Sprint 1 (HU-001 client registration, HU-021 authentication). "
                                + "Tokens are issued by Supabase Auth and must be sent as Bearer tokens."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
