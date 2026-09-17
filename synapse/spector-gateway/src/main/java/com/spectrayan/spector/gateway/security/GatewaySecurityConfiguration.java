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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Reactive security configuration for spector-gateway (ADR-0081 §8 Phase 3).
 *
 * <p><strong>CSRF</strong>: Disabled. The gateway is a stateless reverse proxy that
 * authenticates via header-based tokens (JWT/API-key). It has no cookie sessions,
 * no login forms, and no UI pages. CSRF protection is not applicable per
 * <a href="https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html">
 * OWASP CSRF Cheat Sheet</a>: "If your application does not maintain state via cookies,
 * CSRF is not a risk."
 *
 * <p><strong>CORS</strong>: Configurable via {@code spector.cors.*} properties, mirroring the
 * synapse {@code CorsProperties} pattern.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityConfiguration.class);

    @Bean
    @SuppressWarnings("java:S4502") // Sonar: CSRF disable is intentional — documented stateless proxy exception
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            GatewayProperties properties,
            CorsConfigurationSource corsSource,
            Environment env
    ) {
        // CORS — delegate to configurable CorsConfigurationSource bean
        http.cors(cors -> cors.configurationSource(corsSource));

        // CSRF — disabled (documented exception).
        // This is a stateless reverse proxy with no cookies, no sessions, and no UI.
        // All authentication is header-based (JWT / API-key). CSRF is not applicable.
        http.csrf(ServerHttpSecurity.CsrfSpec::disable); // lgtm[java/spring-disabled-csrf]

        // Stateless — no form login or HTTP basic
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);

        if (properties.getAuth() == null || !properties.getAuth().isEnabled()) {
            return http
                    .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                    .build();
        }

        // Auth enabled — validate that JWT issuer/JWK is configured
        validateAuthConfiguration(env);

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

    /**
     * Fail closed: if auth is enabled but no JWT issuer-uri or jwk-set-uri is configured,
     * fail startup with a clear error rather than silently misconfiguring security.
     */
    private void validateAuthConfiguration(Environment env) {
        String issuerUri = env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        String jwkSetUri = env.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri");

        if ((issuerUri == null || issuerUri.isBlank()) && (jwkSetUri == null || jwkSetUri.isBlank())) {
            throw new IllegalStateException(
                    "spector.auth.enabled=true but no JWT configuration found. "
                    + "Set spring.security.oauth2.resourceserver.jwt.issuer-uri or "
                    + "spring.security.oauth2.resourceserver.jwt.jwk-set-uri, "
                    + "or disable auth with spector.auth.enabled=false."
            );
        }
        log.info("Gateway auth enabled with JWT issuer-uri={}, jwk-set-uri={}", issuerUri, jwkSetUri);
    }

    /**
     * Reactive CORS configuration source derived from {@code spector.cors.*} properties.
     * Mirrors the synapse WebConfig CORS pattern for consistency.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        GatewayProperties.CorsProperties corsProps = properties.getCors();

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(
                Arrays.stream(corsProps.getAllowedOrigins().split(","))
                        .map(String::trim)
                        .toList());

        configuration.setAllowedMethods(
                Arrays.stream(corsProps.getAllowedMethods().split(","))
                        .map(String::trim)
                        .toList());

        configuration.setAllowedHeaders(
                Arrays.stream(corsProps.getAllowedHeaders().split(","))
                        .map(String::trim)
                        .toList());

        configuration.setExposedHeaders(
                Arrays.stream(corsProps.getExposedHeaders().split(","))
                        .map(String::trim)
                        .toList());

        configuration.setAllowCredentials(corsProps.isAllowCredentials());
        configuration.setMaxAge(corsProps.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
