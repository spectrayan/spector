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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.Pbkdf2Properties;
import com.spectrayan.spector.synapse.security.ApiKeyAuthenticationFilter;
import com.spectrayan.spector.synapse.security.FailClosedAccessDeniedHandler;
import com.spectrayan.spector.synapse.security.FailClosedAuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Security composition root for Spector Synapse.
 *
 * <p>This is the single Spring Security assembly point. It wires the password encoder, the
 * {@link DaoAuthenticationProvider} (backed by the JDBC-backed {@link UserDetailsService} and the
 * {@link Pbkdf2PasswordEncoder}), the {@link AuthenticationManager} consumed by the auth REST
 * surface, the OAuth2 Resource Server (issuer-routing JWT validation), the extended
 * {@link ApiKeyAuthenticationFilter}, and the request-authorization policy.</p>
 *
 * <p>Behavior is split on {@code spector.auth.enabled} (Requirement 1):</p>
 * <ul>
 *   <li><strong>{@code false} (legacy default)</strong>: every path is {@code permitAll} and the
 *       {@link ApiKeyAuthenticationFilter} keeps its legacy shared-key {@code ROLE_API} path — the
 *       server behaves exactly as it does today. Exactly one startup WARN is emitted stating the
 *       server is unauthenticated and must not be exposed beyond localhost (Requirements 1.1,
 *       1.6).</li>
 *   <li><strong>{@code true}</strong>: sessions are stateless, CSRF is disabled (stateless,
 *       cookie-less), {@code spector.auth.public-paths} plus the static-asset/root paths are
 *       permitted, {@code /api/**} and {@code /mcp} require a non-anonymous {@code Authentication},
 *       the OAuth2 Resource Server is enabled with the issuer-routing {@code JwtDecoder}, and
 *       method security ({@code @PreAuthorize}) is active for scope/role gating (Requirements 6.1,
 *       6.2, 6.3, 6.4, 6.5).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Salt length (bytes) matching {@code Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()},
     * i.e. the encoding produced by the {@code {pbkdf2}} entry of Spring Security's delegating
     * password encoder.
     */
    private static final int PBKDF2_SALT_LENGTH = 16;

    /** {@code true} when {@code spector.auth.enabled=true}; captured for the startup WARN. */
    private final boolean authEnabled;

    /**
     * Captures the auth toggle so the startup WARN (Requirement 1.6) can be emitted exactly once
     * when the feature is disabled.
     *
     * @param properties bound {@code spector.*} configuration
     */
    public SecurityConfig(SynapseProperties properties) {
        this.authEnabled = properties.auth().enabled();
    }

    /**
     * Password encoder used to hash and verify user passwords.
     *
     * <p>Configured as a {@link Pbkdf2PasswordEncoder} whose iteration count is read from
     * {@code spector.auth.pbkdf2.iterations} (default {@code 310000}, OWASP-2024 baseline). The
     * {@link Pbkdf2Properties} compact constructor already coerces absent/&lt;1 values to the
     * documented default, so any value observed here is a valid iteration count (Requirements 14.6,
     * 14.7).</p>
     *
     * <p>Salt length ({@value #PBKDF2_SALT_LENGTH} bytes) and the {@code PBKDF2WithHmacSHA256}
     * algorithm match Spring Security's {@code defaultsForSpringSecurity_v5_8()} encoding, making
     * the output suitable for use behind a delegating {@code {pbkdf2}} scheme (Requirement 14.1).
     * The encoder applies an independent per-encode salt and performs a constant-time
     * {@code matches} comparison out of the box.</p>
     *
     * @param properties bound {@code spector.*} configuration
     * @return the shared PBKDF2 password encoder
     */
    @Bean
    public Pbkdf2PasswordEncoder passwordEncoder(SynapseProperties properties) {
        int iterations = properties.auth().pbkdf2().iterations();
        log.debug("Configuring Pbkdf2PasswordEncoder with {} iterations", iterations);
        return new Pbkdf2PasswordEncoder(
                "",
                PBKDF2_SALT_LENGTH,
                iterations,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    }

    /**
     * Username/password authentication provider backed by the JDBC {@link UserDetailsService} and
     * the {@link Pbkdf2PasswordEncoder}. It performs the account checks (enabled, non-locked) and
     * the constant-time {@code {pbkdf2}} password comparison used by the login flow.
     *
     * @param userDetailsService the JDBC-backed user-details lookup (principal name == user TSID)
     * @param passwordEncoder    the PBKDF2 encoder used for verification
     * @return the configured DAO authentication provider
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService,
                                                               Pbkdf2PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * The {@link AuthenticationManager} consumed by {@code AuthController} (task 13.2) to
     * authenticate username/password logins. Delegates to the {@link DaoAuthenticationProvider}.
     *
     * @param daoAuthenticationProvider the DAO provider performing credential verification
     * @return a {@link ProviderManager} over the DAO provider
     */
    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider) {
        return new ProviderManager(daoAuthenticationProvider);
    }

    /**
     * Assembles the Spring Security filter chain.
     *
     * <p>When {@code spector.auth.enabled=false} the chain preserves today's behavior exactly:
     * {@code permitAll} for every request while the {@link ApiKeyAuthenticationFilter} keeps its
     * legacy shared-key {@code ROLE_API} path. When enabled, public paths are permitted, {@code
     * /api/**} and {@code /mcp} require authentication, and the OAuth2 Resource Server is wired with
     * the issuer-routing {@code AuthenticationManagerResolver}. In both modes the extended API-key
     * filter runs before {@link UsernamePasswordAuthenticationFilter}.</p>
     *
     * @param http                the {@link HttpSecurity} builder
     * @param apiKeyFilter        the extended API-key authentication filter (Spring bean)
     * @param properties          bound {@code spector.*} configuration
     * @param jwtResolverProvider provider for the issuer-routing JWT resolver, present only when
     *                            {@code spector.auth.enabled=true}
     * @return the built {@link SecurityFilterChain}
     * @throws Exception if the {@link HttpSecurity} build fails
     */
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ApiKeyAuthenticationFilter apiKeyFilter,
            SynapseProperties properties,
            ObjectProvider<AuthenticationManagerResolver<HttpServletRequest>> jwtResolverProvider)
            throws Exception {

        AuthProperties auth = properties.auth();

        http
                .cors(cors -> {})  // Enable CORS — delegates to WebMvcConfigurer bean
                // codeql[java/spring-disabled-csrf-protection] - Disabled because authentication is stateless and cookie-less
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (auth.enabled()) {
            // Fail-closed error responses (Requirements 6.3, 6.4, 19.1, 19.6): unauthenticated
            // access to a protected path yields a uniform 401 and insufficient authority a 403,
            // each a minimal JSON body that excludes secrets and raw API keys. Instantiated locally
            // so the mapping is self-contained to this filter chain and adds no shared beans.
            AuthenticationEntryPoint authEntryPoint = new FailClosedAuthenticationEntryPoint();
            AccessDeniedHandler accessDeniedHandler = new FailClosedAccessDeniedHandler();
            http.exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));

            List<String> publicPaths = auth.publicPaths();
            http.authorizeHttpRequests(authz -> {
                // Configured public paths bypass authentication (Requirement 6.2).
                for (String path : publicPaths) {
                    authz.requestMatchers(path).permitAll();
                    authz.requestMatchers(path + "/**").permitAll();
                }
                // Static assets and the SPA root remain public.
                authz
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/index.html").permitAll()
                        .requestMatchers("/assets/**").permitAll()
                        .requestMatchers("/*.js", "/*.css", "/*.ico", "/*.png").permitAll()
                        // Protected surfaces require a non-anonymous Authentication (Requirement 6.1).
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/mcp").authenticated()
                        .anyRequest().authenticated();
            });

            // Enable bearer-token validation via the issuer-routing resolver (server HS256 + OIDC
            // RS256). The resolver bean is @ConditionalOnProperty(spector.auth.enabled=true), so it
            // is only present — and only wired — when auth is enabled.
            AuthenticationManagerResolver<HttpServletRequest> jwtResolver = jwtResolverProvider.getIfAvailable();
            if (jwtResolver != null) {
                // Route the resource server's own authentication/authorization failures (invalid,
                // expired, or wrong-issuer bearer tokens) through the same fail-closed responders so
                // every 401/403 body is uniform and secret-free (Requirements 19.1, 19.6).
                http.oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(jwtResolver)
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
            }
        } else {
            // Legacy behavior: permit every path; the API-key filter still binds ROLE_API for the
            // configured shared key (Requirements 1.1, 1.2).
            http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        }

        http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Emits exactly one warning-level log entry at startup when authentication is disabled,
     * advising operators not to expose the (unauthenticated) server beyond localhost
     * (Requirement 1.6).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnWhenUnauthenticated() {
        if (!authEnabled) {
            log.warn("spector.auth.enabled=false: this server is UNAUTHENTICATED (legacy shared-key "
                    + "ROLE_API only). Do NOT expose it beyond localhost. Set spector.auth.enabled=true "
                    + "to require authentication.");
        }
    }
}
