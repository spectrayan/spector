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
package org.spectrayan.testfixtures.synapse.auth;

import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.spectrayan.spector.synapse.config.SecurityConfig;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.security.ApiKeyAuthenticationFilter;
import com.spectrayan.spector.synapse.security.ApiKeyStore;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Test fixture (not production code) supplying the production {@code SecurityConfig} filter chain,
 * the API-key filter, and the probe controller for the security-config authorization gating test.
 *
 * <p>Deliberately lives OUTSIDE the {@code com.spectrayan} component-scan base package tree so that
 * {@code @SpringBootApplication} scanning in unrelated {@code @SpringBootTest} contexts never picks
 * up these configurations.</p>
 */
public abstract class SecurityGatingChainConfig {

    abstract boolean authEnabled();

    @Bean
    SynapseProperties synapseProperties() {
        AuthProperties auth = new AuthProperties(authEnabled(), null, null, null, null, null, null, null);
        return new SynapseProperties(7070, "shared-key", "./spector-data", null, null, null, auth);
    }

    @Bean
    ApiKeyStore apiKeyStore() {
        return Mockito.mock(ApiKeyStore.class);
    }

    @Bean
    ApiKeyAuthenticationFilter apiKeyFilter(SynapseProperties properties, ApiKeyStore apiKeyStore) {
        return new ApiKeyAuthenticationFilter(properties, apiKeyStore);
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            ApiKeyAuthenticationFilter apiKeyFilter,
            SynapseProperties properties,
            ObjectProvider<AuthenticationManagerResolver<HttpServletRequest>> jwtResolverProvider)
            throws Exception {
        // Drive the PRODUCTION security composition root directly.
        return new SecurityConfig(properties).filterChain(http, apiKeyFilter, properties, jwtResolverProvider);
    }

    @Bean
    SecurityGatingProbeController probeController() {
        return new SecurityGatingProbeController();
    }

    /** Chain built from the production {@code SecurityConfig} with {@code spector.auth.enabled=true}. */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    public static class AuthEnabled extends SecurityGatingChainConfig {
        @Override
        boolean authEnabled() {
            return true;
        }
    }

    /** Chain built from the production {@code SecurityConfig} with {@code spector.auth.enabled=false}. */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    public static class AuthDisabled extends SecurityGatingChainConfig {
        @Override
        boolean authEnabled() {
            return false;
        }
    }
}
