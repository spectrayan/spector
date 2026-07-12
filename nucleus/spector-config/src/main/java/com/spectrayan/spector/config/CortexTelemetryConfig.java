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
package com.spectrayan.spector.config;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuration for the Cortex telemetry pipeline.
 *
 * <p>Follows the Spring Boot Micrometer configuration pattern where each
 * property has a sensible default and can be overridden via system properties
 * (e.g., {@code -Dspector.cortex.interval=3000}).</p>
 *
 * <h3>System Properties</h3>
 * <table>
 *   <tr><th>Property</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>{@code spector.cortex.enabled}</td><td>{@code true}</td><td>Enable/disable cortex telemetry entirely</td></tr>
 *   <tr><td>{@code spector.cortex.interval}</td><td>{@code 2000}</td><td>Diagnostic snapshot interval in milliseconds</td></tr>
 *   <tr><td>{@code spector.cortex.query.perQuery}</td><td>{@code true}</td><td>Emit per-query trace events</td></tr>
 *   <tr><td>{@code spector.cortex.query.sampleRate}</td><td>{@code 1.0}</td><td>Sampling rate for per-query events (0.0-1.0)</td></tr>
 *   <tr><td>{@code spector.cortex.simd.enabled}</td><td>{@code true}</td><td>Emit SIMD lane events</td></tr>
 *   <tr><td>{@code spector.cortex.graph.enabled}</td><td>{@code true}</td><td>Emit graph pulse events</td></tr>
 * </table>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // Use defaults
 *   CortexTelemetryConfig config = CortexTelemetryConfig.fromSystemProperties();
 *
 *   // Override in tests
 *   CortexTelemetryConfig config = new CortexTelemetryConfig(true, 5000, true, 0.1, true, true);
 * }</pre>
 *
 * @param enabled          master switch for cortex telemetry
 * @param intervalMs       diagnostic snapshot interval in milliseconds
 * @param perQueryEnabled  whether to emit per-query trace events
 * @param querySampleRate  sampling rate for per-query events (1.0 = every query, 0.1 = 10%)
 * @param simdEnabled      whether to emit SIMD lane telemetry
 * @param graphEnabled     whether to emit graph pulse telemetry
 */
public record CortexTelemetryConfig(
        boolean enabled,
        long intervalMs,
        boolean perQueryEnabled,
        double querySampleRate,
        boolean simdEnabled,
        boolean graphEnabled
) {

    /** Default configuration: all enabled, 2s interval, 100% query sampling. */
    public static final CortexTelemetryConfig DEFAULT =
            new CortexTelemetryConfig(true, 2000, true, 1.0, true, true);

    /**
     * Creates a configuration by reading system properties with fallback defaults.
     *
     * @return configuration populated from system properties
     */
    public static CortexTelemetryConfig fromSystemProperties() {
        return new CortexTelemetryConfig(
                boolProp("spector.cortex.enabled", true),
                longProp("spector.cortex.interval", 2000),
                boolProp("spector.cortex.query.perQuery", true),
                doubleProp("spector.cortex.query.sampleRate", 1.0),
                boolProp("spector.cortex.simd.enabled", true),
                boolProp("spector.cortex.graph.enabled", true)
        );
    }

    /**
     * Returns whether a given query should be sampled for per-query events.
     * Uses a probabilistic sampling approach based on {@link #querySampleRate()}.
     *
     * @return true if this query should be traced
     */
    public boolean shouldSampleQuery() {
        if (!perQueryEnabled) return false;
        if (querySampleRate >= 1.0) return true;
        if (querySampleRate <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble() < querySampleRate;
    }

    private static boolean boolProp(String key, boolean defaultValue) {
        String val = System.getProperty(key);
        return val != null ? Boolean.parseBoolean(val) : defaultValue;
    }

    private static long longProp(String key, long defaultValue) {
        String val = System.getProperty(key);
        if (val == null) return defaultValue;
        try { return Long.parseLong(val); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private static double doubleProp(String key, double defaultValue) {
        String val = System.getProperty(key);
        if (val == null) return defaultValue;
        try { return Double.parseDouble(val); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
