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
package com.spectrayan.spector.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Health check result for a registered provider.
 *
 * <p>Used by {@link ProviderRegistry} to report provider availability.
 * Captures the provider name, health status, response latency, an optional
 * diagnostic message, and the timestamp of the check.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   ProviderHealth health = registry.health("openai");
 *   if (health.status() == ProviderHealth.Status.HEALTHY) {
 *       log.info("OpenAI is up, latency: {}ms", health.latency().toMillis());
 *   }
 * }</pre>
 *
 * @param name      the provider instance name
 * @param status    health status
 * @param latency   response latency of the health check
 * @param message   optional diagnostic message (empty if healthy)
 * @param checkedAt timestamp when the check was performed
 */
public record ProviderHealth(
        String name,
        Status status,
        Duration latency,
        String message,
        Instant checkedAt
) {

    /**
     * Provider health status.
     */
    public enum Status {
        /** Provider is responsive and accepting requests. */
        HEALTHY,
        /** Provider is not responding or returning errors. */
        UNHEALTHY,
        /** Provider health has not been checked yet. */
        UNKNOWN
    }

    /**
     * Compact constructor — validates required fields.
     */
    public ProviderHealth {
        Objects.requireNonNull(name, "Provider name must not be null");
        Objects.requireNonNull(status, "Provider status must not be null");
        Objects.requireNonNull(latency, "Latency must not be null");
        Objects.requireNonNull(checkedAt, "CheckedAt must not be null");
        message = message == null ? "" : message;
    }

    /**
     * Creates a healthy result.
     *
     * @param name    provider name
     * @param latency measured latency
     * @return a healthy {@link ProviderHealth}
     */
    public static ProviderHealth healthy(String name, Duration latency) {
        return new ProviderHealth(name, Status.HEALTHY, latency, "", Instant.now());
    }

    /**
     * Creates an unhealthy result with a diagnostic message.
     *
     * @param name    provider name
     * @param message diagnostic message describing the failure
     * @return an unhealthy {@link ProviderHealth}
     */
    public static ProviderHealth unhealthy(String name, String message) {
        return new ProviderHealth(name, Status.UNHEALTHY, Duration.ZERO, message, Instant.now());
    }

    /**
     * Creates an unknown-status result (not yet checked).
     *
     * @param name provider name
     * @return an unknown {@link ProviderHealth}
     */
    public static ProviderHealth unknown(String name) {
        return new ProviderHealth(name, Status.UNKNOWN, Duration.ZERO, "Not yet checked", Instant.now());
    }

    /**
     * Returns whether this provider is healthy.
     *
     * @return {@code true} if status is {@link Status#HEALTHY}
     */
    public boolean isHealthy() {
        return status == Status.HEALTHY;
    }
}
