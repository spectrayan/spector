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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Detects Ollama LLM provider availability on application startup.
 *
 * <p>On {@link ApplicationReadyEvent}, this component probes the Ollama
 * {@code /api/tags} endpoint to determine whether an LLM provider is
 * reachable. The detection result is exposed via {@link #isOllamaAvailable()}
 * for other components (e.g., feature flag auto-enablement).</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li>Uses {@link java.net.http.HttpClient} with virtual thread executor
 *       to avoid blocking platform threads during detection.</li>
 *   <li>5-second timeout prevents slow/unreachable Ollama from delaying startup.</li>
 *   <li>Never fails startup — logs a warning and sets availability to {@code false}.</li>
 * </ul>
 *
 * @see SynapseProperties.OllamaProperties
 */
@Component
public class OllamaDetector {

    private static final Logger log = LoggerFactory.getLogger(OllamaDetector.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final SynapseProperties properties;
    private volatile boolean ollamaAvailable;

    /**
     * Constructs the detector with Synapse configuration.
     *
     * @param properties the Synapse properties containing Ollama base URL
     */
    public OllamaDetector(SynapseProperties properties) {
        this.properties = properties;
    }

    /**
     * Probes Ollama on application startup.
     *
     * <p>Called automatically by Spring when the application context
     * is fully initialized. Sends a GET request to {@code /api/tags}
     * and checks for an HTTP 200 response.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void detectOllama() {
        String baseUrl = properties.ollama().baseUrl();
        String healthUrl = baseUrl + "/api/tags";

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .executor(executor)
                    .build();

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(CONNECT_TIMEOUT)
                    .GET()
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() == 200) {
                ollamaAvailable = true;
                log.info("🟢 Ollama detected at {} — LLM features available", baseUrl);
            } else {
                ollamaAvailable = false;
                log.warn("🟡 Ollama responded with status {} at {} — LLM features disabled",
                        response.statusCode(), baseUrl);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ollamaAvailable = false;
            log.warn("🔴 Ollama detection interrupted — LLM features disabled");
        } catch (Exception e) {
            ollamaAvailable = false;
            log.warn("🔴 Ollama not reachable at {} — LLM features disabled. Reason: {}",
                    baseUrl, e.getMessage());
        }
    }

    /**
     * Returns whether Ollama was detected as available at startup.
     *
     * @return {@code true} if Ollama is reachable, {@code false} otherwise
     */
    public boolean isOllamaAvailable() {
        return ollamaAvailable;
    }
}
