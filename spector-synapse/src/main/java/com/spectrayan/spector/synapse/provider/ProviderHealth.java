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
package com.spectrayan.spector.synapse.provider;

import java.time.Duration;
import java.time.Instant;

/**
 * Health check result for a provider.
 *
 * @param status    health status
 * @param name      provider name
 * @param latency   time taken to check health
 * @param message   human-readable message (error details on failure)
 * @param checkedAt when the check was performed
 */
public record ProviderHealth(
        Status status,
        String name,
        Duration latency,
        String message,
        Instant checkedAt
) {
    public enum Status {
        HEALTHY, UNHEALTHY, UNKNOWN
    }

    /** Creates a healthy result. */
    public static ProviderHealth healthy(String name, Duration latency) {
        return new ProviderHealth(Status.HEALTHY, name, latency, "OK", Instant.now());
    }

    /** Creates an unhealthy result. */
    public static ProviderHealth unhealthy(String name, Duration latency, String message) {
        return new ProviderHealth(Status.UNHEALTHY, name, latency, message, Instant.now());
    }

    /** Creates an unknown result (provider not found). */
    public static ProviderHealth unknown(String name) {
        return new ProviderHealth(Status.UNKNOWN, name, Duration.ZERO,
                "Provider not registered", Instant.now());
    }

    public boolean isHealthy() {
        return status == Status.HEALTHY;
    }
}
