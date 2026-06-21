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
 * Sealed base interface for all lightweight telemetry events.
 *
 * <p>These are <em>raw telemetry data</em> — performance metrics, diagnostic
 * snapshots, and cluster state observations. They carry timestamps and event
 * types as required by {@link SpectorEvent}, enabling transport to Kafka,
 * MQTT, or any external telemetry pipeline.</p>
 *
 * <h3>Design Rationale</h3>
 * <ul>
 *   <li>Lightweight records — minimal allocations beyond the record itself</li>
 *   <li>Sealed — exhaustive pattern matching in subscribers</li>
 *   <li>Transport-agnostic — no dependency on SSE, Armeria, or web frameworks</li>
 *   <li>Instance-scoped via {@link EventBus} — HA-safe, no global state</li>
 * </ul>
 *
 * @see EventBus
 * @see TelemetryScope
 * @see SpectorEvent
 */
public sealed interface SpectorTelemetryEvent extends SpectorEvent permits
        SimdKernelTelemetry,
        GraphPulseTelemetry,
        GpuKernelTelemetry,
        QueryTraceTelemetry,
        MemorySnapshotTelemetry,
        MemoryDiagnosticTelemetry,
        ReflectCycleTelemetry,
        ClusterTopologyTelemetry,
        EmbeddingProjectionTelemetry {
}
