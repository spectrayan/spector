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
package com.spectrayan.spector.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * Main application entrypoint for the Spector Dedicated Reactive Cell Ingress Router (ADR-0081).
 *
 * <p>Refuses to start if {@code spector.cell.role} is not explicitly set to {@code gateway}.</p>
 */
@SpringBootApplication(scanBasePackages = "com.spectrayan.spector.gateway")
public class SpectorGatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(SpectorGatewayApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SpectorGatewayApplication.class);
        app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> {
            Environment env = event.getEnvironment();
            String role = env.getProperty("spector.cell.role");
            if (role == null || !"gateway".equalsIgnoreCase(role.trim())) {
                String error = "Spector Gateway process MUST be configured with spector.cell.role=gateway (found: '" + role + "'). Aborting startup (ADR-0081 §8 Phase 2).";
                log.error(error);
                throw new IllegalStateException(error);
            }
        });
        app.run(args);
    }
}
