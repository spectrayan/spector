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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * OAuth2 Resource Server {@link JwtDecoder} configuration for <strong>server-issued HS256</strong>
 * bearer tokens (Requirement 3).
 *
 * <p>This class is the dedicated composition point for JWT decoding. It intentionally lives apart
 * from {@code SecurityConfig} so the filter chain and the token-validation policy can evolve
 * independently:</p>
 * <ul>
 *   <li><strong>Task 10.1</strong> contributes the server HS256 decoder
 *       ({@link #serverJwtDecoder}) and its authorities converter
 *       ({@link #serverJwtAuthenticationConverter}).</li>
 *   <li><strong>Task 10.2</strong> adds the external OIDC RS256 decoder ({@link #oidcJwtDecoder},
 *       registered only when {@code spector.auth.oidc.jwks-url} is non-empty) plus issuer-based
 *       routing ({@link #jwtAuthenticationManagerResolver}, a
 *       {@code JwtIssuerAuthenticationManagerResolver}) that selects the validating decoder by the
 *       token's {@code iss} claim (Requirement 4).</li>
 *   <li><strong>Task 11.1</strong> wires the issuer-routing resolver into the resource-server
 *       filter chain in {@code SecurityConfig}.</li>
 * </ul>
 *
 * <p>The beans are only registered when {@code spector.auth.enabled=true}; when the feature is off
 * the server preserves its legacy single-user behavior and no bearer-token validation is required.
 * When enabled, {@code AuthPropertiesValidator} has already guaranteed that
 * {@code spector.auth.jwt.secret} resolves to a non-empty value.</p>
 *
 * <p><strong>Server issuer.</strong> There is no dedicated {@code spector.auth.jwt.issuer}
 * configuration property (see {@link AuthProperties.JwtProperties}). Server-minted tokens therefore
 * use the fixed, documented issuer {@value #SERVER_ISSUER}; the token-minting side (task 13.2) sets
 * the same {@code iss} value. This decoder rejects any server-issued token whose {@code iss} does
 * not equal {@value #SERVER_ISSUER}.</p>
 */
@Configuration
public class JwtDecoderConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtDecoderConfig.class);

    /**
     * Fixed {@code iss} claim value for server-minted HS256 tokens.
     *
     * <p>No {@code spector.auth.jwt.issuer} property exists, so this constant is the single source
     * of truth shared by the token minter (task 13.2) and this validating decoder.</p>
     */
    public static final String SERVER_ISSUER = "spector-synapse";

    /** HMAC key algorithm required by {@link MacAlgorithm#HS256}. */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /** Maximum clock-skew tolerance applied to the {@code exp} claim (Requirements 3.1, 4.2, 4.7). */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    /**
     * Configuration property whose non-empty value enables external OIDC RS256 validation
     * (Requirement 4.1).
     */
    private static final String OIDC_JWKS_URL_PROPERTY = "spector.auth.oidc.jwks-url";

    /**
     * Upper bound on the JWKS fetch (connect + read). Requirement 4.5 requires rejecting a token
     * whose signing key cannot be retrieved within 5 seconds.
     */
    private static final Duration JWKS_FETCH_TIMEOUT = Duration.ofSeconds(5);

    /** Standard JWT claim carrying space-delimited or list-valued OAuth2 scopes. */
    private static final String CLAIM_SCOPE = "scope";

    /** Alternate scope claim name used by some issuers. */
    private static final String CLAIM_SCP = "scp";

    /** Claim carrying the principal's roles. */
    private static final String CLAIM_ROLES = "roles";

    private static final String SCOPE_PREFIX = "SCOPE_";
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Server-issued HS256 {@link JwtDecoder}.
     *
     * <p>Builds a {@link NimbusJwtDecoder} from the symmetric {@code spector.auth.jwt.secret} that:
     * <ul>
     *   <li>verifies the HMAC-SHA256 signature against the shared secret (Requirements 3.1, 3.3,
     *       3.5);</li>
     *   <li>validates the {@code iss} claim equals {@value #SERVER_ISSUER} (Requirement 3.3);</li>
     *   <li>validates the {@code exp} claim with a clock-skew tolerance of {@value #CLOCK_SKEW}
     *       (Requirements 3.1, 3.4); and</li>
     *   <li>requires a non-blank {@code sub} claim (Requirement 3.6).</li>
     * </ul>
     *
     * <p>A malformed or non-HS256 token fails to parse/verify and is surfaced as an
     * invalid-token error (Requirement 3.5).</p>
     *
     * @param auth bound {@code spector.auth.*} configuration
     * @return the validating HS256 decoder
     */
    @Bean
    @ConditionalOnProperty(name = "spector.auth.enabled", havingValue = "true")
    JwtDecoder serverJwtDecoder(AuthProperties auth) {
        byte[] secretBytes = auth.jwt().secret().getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(secretBytes, HMAC_SHA256);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(tokenValidator(SERVER_ISSUER));

        log.debug("Configured server HS256 JwtDecoder (issuer='{}', clockSkew={}s)",
                SERVER_ISSUER, CLOCK_SKEW.toSeconds());
        return decoder;
    }

    /**
     * External OIDC <strong>RS256</strong> {@link JwtDecoder}, registered only when
     * {@code spector.auth.oidc.jwks-url} resolves to a non-empty value (Requirement 4.1).
     *
     * <p>Builds a JWKS-backed {@link NimbusJwtDecoder} from {@code spector.auth.oidc.jwks-url} that:
     * <ul>
     *   <li>verifies the <strong>RS256</strong> signature using the public keys fetched from the
     *       JWKS endpoint (Requirements 4.1, 4.6);</li>
     *   <li>validates the {@code iss} claim equals {@code spector.auth.oidc.issuer}
     *       (Requirements 4.2, 4.4);</li>
     *   <li>validates the {@code exp} claim with a clock-skew tolerance of {@value #CLOCK_SKEW}
     *       (Requirements 4.2, 4.7); and</li>
     *   <li>requires a non-blank {@code sub} claim.</li>
     * </ul>
     *
     * <p>The JWKS fetch is bounded to {@value #JWKS_FETCH_TIMEOUT} (connect + read) so that an
     * unreachable or slow endpoint surfaces as an invalid-token error rather than hanging the
     * request (Requirement 4.5).</p>
     *
     * <p><strong>Why a custom {@link Condition} rather than a bare
     * {@code @ConditionalOnProperty}.</strong> {@code spector.auth.oidc.jwks-url} is declared in
     * {@code application.yml} with an empty default ({@code ${SPECTOR_AUTH_OIDC_JWKS_URL:}}). A
     * plain {@code @ConditionalOnProperty(name = ...)} matches whenever the property is present and
     * not literally {@code "false"} — <em>including the empty string</em> — which would register
     * this bean with a blank JWKS URL and break startup for the common OIDC-disabled deployment.
     * {@link OidcConfiguredCondition} matches only when the value has text, giving the required
     * "non-empty" semantics.</p>
     *
     * @param auth bound {@code spector.auth.*} configuration
     * @return the JWKS-backed RS256 decoder
     */
    @Bean
    @Conditional(OidcConfiguredCondition.class)
    JwtDecoder oidcJwtDecoder(AuthProperties auth) {
        String jwksUrl = auth.oidc().jwksUrl();
        String issuer = auth.oidc().issuer();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUrl)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(boundedJwksRestOperations())
                .build();

        decoder.setJwtValidator(tokenValidator(issuer));

        log.debug("Configured OIDC RS256 JwtDecoder (issuer='{}', jwksFetchTimeout={}s, clockSkew={}s)",
                issuer, JWKS_FETCH_TIMEOUT.toSeconds(), CLOCK_SKEW.toSeconds());
        return decoder;
    }

    /**
     * Issuer-based routing for the OAuth2 Resource Server: selects the validating manager (and thus
     * the decoder) by the token's {@code iss} claim (Requirement 4.3). Task 11.1 wires this bean
     * into the resource-server filter chain.
     *
     * <p>The server issuer ({@value #SERVER_ISSUER}, HS256) is always mapped. When OIDC is
     * configured, {@code spector.auth.oidc.issuer} (RS256) is additionally mapped to the JWKS-backed
     * decoder. A token whose {@code iss} matches no configured issuer — including an external token
     * whose issuer is not {@code spector.auth.oidc.issuer} — resolves to no manager, which the
     * underlying {@link JwtIssuerAuthenticationManagerResolver} surfaces as an invalid-token
     * {@code 401} without establishing a session (Requirement 4.4). When only the server issuer is
     * configured (no OIDC), routing still resolves server tokens.</p>
     *
     * @param auth          bound {@code spector.auth.*} configuration (supplies the OIDC issuer)
     * @param serverDecoder the always-present server HS256 decoder
     * @param oidcDecoder   the OIDC RS256 decoder, present only when {@code jwks-url} is non-empty
     * @return an issuer-routing {@link AuthenticationManagerResolver} over the configured decoders
     */
    @Bean
    @ConditionalOnProperty(name = "spector.auth.enabled", havingValue = "true")
    AuthenticationManagerResolver<HttpServletRequest> jwtAuthenticationManagerResolver(
            AuthProperties auth,
            @Qualifier("serverJwtDecoder") JwtDecoder serverDecoder,
            @Qualifier("oidcJwtDecoder") Optional<JwtDecoder> oidcDecoder) {

        JwtAuthenticationConverter converter = buildJwtAuthenticationConverter();

        Map<String, AuthenticationManager> managersByIssuer = new LinkedHashMap<>();
        managersByIssuer.put(SERVER_ISSUER, authenticationManager(serverDecoder, converter));

        oidcDecoder.ifPresent(decoder -> {
            String issuer = auth.oidc().issuer();
            managersByIssuer.put(issuer, authenticationManager(decoder, converter));
            log.debug("Registered OIDC issuer '{}' for JWT routing", issuer);
        });

        AuthenticationManagerResolver<String> byIssuer = managersByIssuer::get;

        log.debug("Configured JWT issuer routing over {} issuer(s): {}",
                managersByIssuer.size(), managersByIssuer.keySet());
        return new JwtIssuerAuthenticationManagerResolver(byIssuer);
    }

    /**
     * Composes the standard token validator applied to every issuer: {@code exp} with clock-skew,
     * {@code iss} equality, and a required {@code sub}.
     */
    private static OAuth2TokenValidator<Jwt> tokenValidator(String issuer) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtIssuerValidator(issuer),
                subjectRequiredValidator());
    }

    /**
     * Wraps a decoder in a {@link ProviderManager} backed by a {@link JwtAuthenticationProvider}
     * that applies the shared scope/role authorities converter.
     */
    private static AuthenticationManager authenticationManager(
            JwtDecoder decoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> converter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return new ProviderManager(provider);
    }

    /**
     * Builds a {@link RestOperations} whose connect and read timeouts are both bounded to
     * {@value #JWKS_FETCH_TIMEOUT} for the JWKS fetch (Requirement 4.5).
     */
    private static RestOperations boundedJwksRestOperations() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(JWKS_FETCH_TIMEOUT);
        requestFactory.setReadTimeout(JWKS_FETCH_TIMEOUT);
        return new RestTemplate(requestFactory);
    }

    /**
     * Authorities converter for server-issued tokens.
     *
     * <p>Maps the union of the token's {@code scope}/{@code scp} claim (prefixed {@code SCOPE_}) and
     * its {@code roles} claim (prefixed {@code ROLE_}) to Spring Security authorities, and pins the
     * principal name to the {@code sub} claim (the user TSID, never the login username)
     * (Requirement 3.2).</p>
     *
     * @return a converter producing an {@code Authentication} whose authorities are the scope/role
     *         union and whose principal name is the {@code sub} claim
     */
    @Bean
    @ConditionalOnProperty(name = "spector.auth.enabled", havingValue = "true")
    JwtAuthenticationConverter serverJwtAuthenticationConverter() {
        return buildJwtAuthenticationConverter();
    }

    /**
     * Builds a {@link JwtAuthenticationConverter} that pins the principal name to the {@code sub}
     * claim and maps authorities from the scope/role union. Shared by the server converter bean and
     * the issuer-routing managers so every issuer produces identical authorities.
     */
    private static JwtAuthenticationConverter buildJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName(JwtClaimNames.SUB);
        converter.setJwtGrantedAuthoritiesConverter(scopeAndRoleAuthoritiesConverter());
        return converter;
    }

    /**
     * Builds a converter that unions {@code SCOPE_*} authorities (from {@code scope}/{@code scp})
     * and {@code ROLE_*} authorities (from {@code roles}).
     */
    private static Converter<Jwt, Collection<GrantedAuthority>> scopeAndRoleAuthoritiesConverter() {
        return jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            for (String scope : claimValues(jwt, CLAIM_SCOPE)) {
                authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + scope));
            }
            for (String scope : claimValues(jwt, CLAIM_SCP)) {
                authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + scope));
            }
            for (String role : claimValues(jwt, CLAIM_ROLES)) {
                String value = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
                authorities.add(new SimpleGrantedAuthority(value));
            }
            return authorities;
        };
    }

    /**
     * Extracts a claim as a normalized list of non-blank string values, accepting either a
     * whitespace-delimited string (OAuth2 {@code scope} convention) or a JSON array.
     */
    private static List<String> claimValues(Jwt jwt, String claimName) {
        Object raw = jwt.getClaim(claimName);
        List<String> values = new ArrayList<>();
        if (raw == null) {
            return values;
        }
        if (raw instanceof String s) {
            for (String token : s.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    values.add(token);
                }
            }
        } else if (raw instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element != null) {
                    String token = element.toString().trim();
                    if (!token.isBlank()) {
                        values.add(token);
                    }
                }
            }
        }
        return values;
    }

    /**
     * Validator requiring a non-blank {@code sub} claim (Requirement 3.6).
     */
    private static OAuth2TokenValidator<Jwt> subjectRequiredValidator() {
        return jwt -> {
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                OAuth2Error error = new OAuth2Error(
                        "invalid_token",
                        "The required 'sub' claim is missing",
                        null);
                return OAuth2TokenValidatorResult.failure(error);
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    /**
     * Matches only when {@code spector.auth.oidc.jwks-url} resolves to a value with text, giving the
     * "non-empty" enablement semantics required by Requirement 4.1.
     *
     * <p>A bare {@code @ConditionalOnProperty(name = "spector.auth.oidc.jwks-url")} cannot express
     * this: its match rule accepts any present value other than {@code "false"}, so the empty-string
     * default declared in {@code application.yml} would (incorrectly) enable OIDC.</p>
     */
    static final class OidcConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String jwksUrl = context.getEnvironment().getProperty(OIDC_JWKS_URL_PROPERTY);
            return StringUtils.hasText(jwksUrl);
        }
    }
}
