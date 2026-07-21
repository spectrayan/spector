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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.spectrayan.spector.synapse.security.FailClosedAccessDeniedHandler;
import com.spectrayan.spector.synapse.security.FailClosedAuthenticationEntryPoint;

/**
 * Fixture configuration for AuthInvariantProtectedPathsPropertyTest.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableWebMvc
public class AuthInvariantWebSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new FailClosedAuthenticationEntryPoint())
                        .accessDeniedHandler(new FailClosedAccessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/docs").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    public AuthInvariantProbeController probeController() {
        return new AuthInvariantProbeController();
    }
}
