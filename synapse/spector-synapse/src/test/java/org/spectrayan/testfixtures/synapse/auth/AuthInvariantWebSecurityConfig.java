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
