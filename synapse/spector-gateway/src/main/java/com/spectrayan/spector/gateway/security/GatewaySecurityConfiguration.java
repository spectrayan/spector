/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.gateway.security;

import com.spectrayan.spector.gateway.config.GatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive security configuration for spector-gateway (ADR-0081 §8 Phase 3).
 * Validates JWT bearer tokens at the edge while permitting auth issuance and health checks.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            GatewayProperties properties
    ) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        if (properties.getAuth() == null || !properties.getAuth().isEnabled()) {
            return http
                    .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                    .build();
        }

        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**", "/api/v1/health/**", "/health/**").permitAll()
                        .pathMatchers("/api/v1/auth/**").permitAll() // Forwarded to owner for token issuance
                        .pathMatchers("/api/v1/**").authenticated()
                        .anyExchange().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}
