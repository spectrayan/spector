/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.1 configuration for Spector Synapse REST endpoints.
 *
 * <p>Defines global API metadata, security schemes (API Key and OAuth2/JWT Bearer),
 * and default server targets for interactive Swagger UI documentation and SDK generation.</p>
 */
@Configuration
public class OpenApiConfig {

    public static final String API_KEY_SCHEME = "ApiKeyAuth";
    public static final String BEARER_SCHEME = "BearerAuth";

    @Bean
    public OpenAPI spectorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spector Cognitive Memory & Search API")
                        .version("1.0.0")
                        .description("Ultra-fast cognitive memory, vector search, and agent orchestration platform "
                                + "with biological memory tiers (Working, Episodic, Semantic, Procedural).")
                        .contact(new Contact()
                                .name("Spectrayan Maintainers")
                                .email("support@spectrayan.com")
                                .url("https://github.com/spectrayan/spector"))
                        .license(new License()
                                .name("Apache License, Version 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("/").description("Current Server Instance")
                ))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("Spector API Key Authentication"))
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("OAuth2 / OIDC JWT Bearer Token")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
