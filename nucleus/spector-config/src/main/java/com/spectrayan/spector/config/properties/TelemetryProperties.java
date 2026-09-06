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
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuration for the Cortex telemetry pipeline.
 */
public class TelemetryProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final TelemetryProperties DEFAULT =
            new TelemetryProperties(
                    DEFAULT_TELEMETRY_ENABLED,
                    DEFAULT_TELEMETRY_INTERVAL_MS,
                    DEFAULT_TELEMETRY_PER_QUERY_ENABLED,
                    DEFAULT_TELEMETRY_QUERY_SAMPLE_RATE,
                    DEFAULT_TELEMETRY_SIMD_ENABLED,
                    DEFAULT_TELEMETRY_GRAPH_ENABLED
            );

    private boolean enabled;
    private long intervalMs;
    private boolean perQueryEnabled;
    private double querySampleRate;
    private boolean simdEnabled;
    private boolean graphEnabled;

    public TelemetryProperties() {
        this(DEFAULT.enabled, DEFAULT.intervalMs, DEFAULT.perQueryEnabled,
                DEFAULT.querySampleRate, DEFAULT.simdEnabled, DEFAULT.graphEnabled);
    }

    public TelemetryProperties(boolean enabled, long intervalMs, boolean perQueryEnabled,
                                double querySampleRate, boolean simdEnabled, boolean graphEnabled) {
        this.enabled = enabled;
        this.intervalMs = intervalMs;
        this.perQueryEnabled = perQueryEnabled;
        this.querySampleRate = querySampleRate;
        this.simdEnabled = simdEnabled;
        this.graphEnabled = graphEnabled;
    }

    public static TelemetryProperties from(com.spectrayan.spector.config.SpectorConfigSource source) {
        if (source == null) return DEFAULT;
        return new TelemetryProperties(
                source.getBoolean(TELEMETRY_ENABLED, source.getBoolean("spector.cortex.enabled", DEFAULT_TELEMETRY_ENABLED)),
                source.getLong(TELEMETRY_INTERVAL_MS, source.getLong("spector.cortex.interval", DEFAULT_TELEMETRY_INTERVAL_MS)),
                source.getBoolean(TELEMETRY_PER_QUERY_ENABLED, source.getBoolean("spector.cortex.query.perQuery", DEFAULT_TELEMETRY_PER_QUERY_ENABLED)),
                source.getDouble(TELEMETRY_QUERY_SAMPLE_RATE, source.getDouble("spector.cortex.query.sampleRate", DEFAULT_TELEMETRY_QUERY_SAMPLE_RATE)),
                source.getBoolean(TELEMETRY_SIMD_ENABLED, source.getBoolean("spector.cortex.simd.enabled", DEFAULT_TELEMETRY_SIMD_ENABLED)),
                source.getBoolean(TELEMETRY_GRAPH_ENABLED, source.getBoolean("spector.cortex.graph.enabled", DEFAULT_TELEMETRY_GRAPH_ENABLED))
        );
    }

    @Deprecated(forRemoval = true)
    public static TelemetryProperties fromSystemProperties() {
        return from(com.spectrayan.spector.config.SpectorConfigSource.load());
    }

    public boolean shouldSampleQuery() {
        if (!perQueryEnabled) return false;
        if (querySampleRate >= 1.0) return true;
        if (querySampleRate <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble() < querySampleRate;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }

    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    public long intervalMs() { return intervalMs; }

    public boolean isPerQueryEnabled() { return perQueryEnabled; }
    public void setPerQueryEnabled(boolean perQueryEnabled) { this.perQueryEnabled = perQueryEnabled; }
    public boolean perQueryEnabled() { return perQueryEnabled; }

    public double getQuerySampleRate() { return querySampleRate; }
    public void setQuerySampleRate(double querySampleRate) { this.querySampleRate = querySampleRate; }
    public double querySampleRate() { return querySampleRate; }

    public boolean isSimdEnabled() { return simdEnabled; }
    public void setSimdEnabled(boolean simdEnabled) { this.simdEnabled = simdEnabled; }
    public boolean simdEnabled() { return simdEnabled; }

    public boolean isGraphEnabled() { return graphEnabled; }
    public void setGraphEnabled(boolean graphEnabled) { this.graphEnabled = graphEnabled; }
    public boolean graphEnabled() { return graphEnabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TelemetryProperties that = (TelemetryProperties) o;
        return enabled == that.enabled && intervalMs == that.intervalMs &&
                perQueryEnabled == that.perQueryEnabled &&
                Double.compare(that.querySampleRate, querySampleRate) == 0 &&
                simdEnabled == that.simdEnabled && graphEnabled == that.graphEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, intervalMs, perQueryEnabled, querySampleRate, simdEnabled, graphEnabled);
    }

    @Override
    public String toString() {
        return "TelemetryProperties{" +
                "enabled=" + enabled +
                ", intervalMs=" + intervalMs +
                ", perQueryEnabled=" + perQueryEnabled +
                ", querySampleRate=" + querySampleRate +
                ", simdEnabled=" + simdEnabled +
                ", graphEnabled=" + graphEnabled +
                '}';
    }
}
