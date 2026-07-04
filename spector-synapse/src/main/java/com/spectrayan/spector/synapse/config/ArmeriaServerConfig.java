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
package com.spectrayan.spector.synapse.config;

import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.server.cors.CorsService;
import com.linecorp.armeria.server.cors.CorsServiceBuilder;
import com.linecorp.armeria.server.file.FileService;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Armeria server for Spector Synapse.
 *
 * <p>Single port serves: REST API, actuator, static Cortex UI assets, and SSE events.
 * CORS is configured for the Cortex UI development server.</p>
 */
@Configuration
public class ArmeriaServerConfig {

    private static final Logger log = LoggerFactory.getLogger(ArmeriaServerConfig.class);

    @Bean
    public ArmeriaServerConfigurator armeriaServerConfigurator(SynapseProperties props) {
        return serverBuilder -> {
            log.info("[Armeria] Configuring server on port {}", props.port());

            // Serve Cortex UI static assets from classpath:/static/
            serverBuilder.serviceUnder("/", FileService.of(
                    ArmeriaServerConfig.class.getClassLoader(), "static"));

            // CORS for Cortex UI dev server
            String[] origins = props.cors().allowedOrigins().split(",");
            CorsServiceBuilder corsBuilder = CorsService.builderForAnyOrigin();
            for (String origin : origins) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    corsBuilder = CorsService.builder(trimmed);
                }
            }

            serverBuilder.decorator(corsBuilder
                    .allowRequestMethods(HttpMethod.GET, HttpMethod.POST,
                            HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)
                    .allowRequestHeaders(HttpHeaderNames.CONTENT_TYPE,
                            HttpHeaderNames.AUTHORIZATION,
                            HttpHeaderNames.of("X-API-Key"))
                    .exposeHeaders(HttpHeaderNames.CONTENT_TYPE)
                    .allowCredentials()
                    .maxAge(3600)
                    .newDecorator());

            log.info("[Armeria] CORS enabled for origins: {}", props.cors().allowedOrigins());
        };
    }
}
