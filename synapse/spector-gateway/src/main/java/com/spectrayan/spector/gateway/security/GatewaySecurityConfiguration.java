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
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Reactive security configuration for spector-gateway (ADR-0081 §8 Phase 3).
 *
 * <p><strong>CSRF</strong>: Stateless REST API endpoints use header-based tokens (JWT/API-key)
 * and omit CSRF cookies, so CSRF protection is selectively ignored on configurable API paths
 * ({@code /api/**}, {@code /actuator/**} by default) rather than blanket-disabled.
 * The ignored paths are configurable via {@code spector.csrf.ignored-paths}.
 *
 * <p><strong>CORS</strong>: Configurable via {@code spector.cors.*} properties, mirroring the
 * synapse {@code CorsProperties} pattern.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            GatewayProperties properties,
            CorsConfigurationSource corsSource
    ) {
        // CORS — delegate to configurable CorsConfigurationSource bean
        http.cors(cors -> cors.configurationSource(corsSource));

        // CSRF — selectively ignore stateless API and actuator paths (configurable).
        // The requireCsrfProtectionMatcher defines which requests REQUIRE CSRF protection.
        // We negate the ignored-paths so that only non-API/non-actuator paths require CSRF.
        // This avoids blanket csrf.disable() which triggers CodeQL security alerts.
        String[] csrfIgnoredPaths = properties.getCsrf().getIgnoredPaths();
        List<ServerWebExchangeMatcher> ignoredMatchers = Arrays.stream(csrfIgnoredPaths)
                .map(String::trim)
                .map(PathPatternParserServerWebExchangeMatcher::new)
                .map(m -> (ServerWebExchangeMatcher) m)
                .toList();

        ServerWebExchangeMatcher csrfRequiredMatcher = new NegatedServerWebExchangeMatcher(
                new OrServerWebExchangeMatcher(ignoredMatchers));

        http.csrf(csrf -> csrf.requireCsrfProtectionMatcher(csrfRequiredMatcher));

        // Session — stateless
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);

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
