package com.spectrayan.spector.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;

/**
 * Utility to bind common JVM and system metrics to a Micrometer {@link MeterRegistry}.
 * Useful for standalone deployments (like Spector Server) that don't have
 * Spring Boot's automatic Actuator binder support.
 */
public final class SpectorJvmMetrics {

    private SpectorJvmMetrics() {
        // Utility class
    }

    /**
     * Binds JVM Memory, GC, Thread, and Processor/System metrics to the given registry.
     *
     * @param registry the Micrometer registry to bind metrics to
     */
    public static void bind(MeterRegistry registry) {
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
    }
}
