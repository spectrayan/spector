package com.spectrayan.spector.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Global {@link MeterRegistry} holder for Spector observability.
 *
 * <p>By default, uses a {@link SimpleMeterRegistry} which silently discards
 * all metrics — zero overhead when observability is not configured.
 * Call {@link #init(MeterRegistry)} at startup to wire a real registry
 * (Prometheus, Datadog, JMX, etc.).</p>
 *
 * <h3>Standalone Usage (Javalin / MCP)</h3>
 * <pre>{@code
 *   var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
 *   SpectorMetrics.init(registry);
 * }</pre>
 *
 * <h3>Spring Boot Usage</h3>
 * <p>Spring auto-configuration calls {@code SpectorMetrics.init(springRegistry)}
 * automatically — no user action required.</p>
 */
public final class SpectorMetrics {

    private static volatile MeterRegistry registry = new SimpleMeterRegistry();

    private SpectorMetrics() {}

    /**
     * Initializes the global meter registry.
     *
     * <p>Should be called once at application startup, before any metrics
     * are recorded. Thread-safe via volatile write.</p>
     *
     * @param registry the meter registry to use
     * @throws NullPointerException if registry is null
     */
    public static void init(MeterRegistry registry) {
        if (registry == null) {
            throw new NullPointerException("MeterRegistry must not be null");
        }
        SpectorMetrics.registry = registry;
    }

    /**
     * Returns the current meter registry.
     *
     * @return the active meter registry (never null)
     */
    public static MeterRegistry registry() {
        return registry;
    }
}
