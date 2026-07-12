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
package com.spectrayan.spector.events;

/**
 * {@link ScopedValue}-based carrier for the telemetry {@link EventBus}.
 *
 * <p>Allows telemetry events to be published from anywhere in the call stack
 * without constructor injection. The bus is bound at the top of a request
 * (e.g., in {@code SearchService}) and automatically available to all
 * sub-components (SIMD kernels, graph traversals, GPU launches, etc.).</p>
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>{@code SpectorNode} creates an {@link EventBus EventBus&lt;SpectorTelemetryEvent&gt;} instance</li>
 *   <li>{@code SearchService} binds it via
 *       {@code ScopedValue.where(TelemetryScope.BUS, bus).call(() -> ...)}</li>
 *   <li>Internal code calls {@code TelemetryScope.publish(new SimdKernelTelemetry(...))}
 *       — if a bus is bound, the event is delivered; otherwise it's a no-op</li>
 * </ol>
 *
 * <h3>HA Safety</h3>
 * <p>{@link ScopedValue} is thread-local (inherited by virtual threads) and
 * per-call-stack. Multiple {@code SpectorNode} instances in the same JVM
 * each get their own bus — no global state, no overwrite risk.</p>
 *
 * <h3>Performance</h3>
 * <p>{@code ScopedValue.isBound()} is approximately 1ns on modern JVMs —
 * comparable to the {@code volatile} read in the old static-holder pattern.</p>
 *
 * @see EventBus
 * @see SpectorTelemetryEvent
 */
public final class TelemetryScope {

    /** The scoped telemetry event bus — bound per request call stack. */
    public static final ScopedValue<EventBus<SpectorTelemetryEvent>> BUS = ScopedValue.newInstance();

    private TelemetryScope() {}

    /**
     * Publishes a telemetry event if a bus is bound in the current scope.
     *
     * <p>This is the primary API for internal code. If no bus is bound
     * (e.g., in unit tests or non-instrumented code paths), this is a no-op.</p>
     *
     * @param event the telemetry event to publish
     * @see SpectorTelemetryEvent
     */
    public static void publish(SpectorTelemetryEvent event) {
        if (BUS.isBound()) {
            BUS.get().publish(event);
        }
    }

    /**
     * Returns true if a telemetry bus is bound in the current scope.
     *
     * <p>Use this to guard expensive telemetry data collection (e.g.,
     * building a projection matrix) that should be skipped when telemetry
     * is not active.</p>
     *
     * @return true if telemetry is active in this call stack
     */
    public static boolean isActive() {
        return BUS.isBound();
    }
}
