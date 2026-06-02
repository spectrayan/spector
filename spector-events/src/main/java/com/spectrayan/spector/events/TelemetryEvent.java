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
 * <p>These are <em>raw telemetry data</em> — they do NOT carry node identity
 * or timestamps. The node layer ({@code SpectorNode}) adds those when
 * converting to {@code SpectorEvent} records for SSE transport.</p>
 *
 * <h3>Design Rationale</h3>
 * <ul>
 *   <li>Lightweight records — no allocations beyond the record itself</li>
 *   <li>Sealed — exhaustive pattern matching in subscribers</li>
 *   <li>Transport-agnostic — no dependency on SSE, Armeria, or web frameworks</li>
 *   <li>Instance-scoped via {@link TelemetryBus} — HA-safe, no global state</li>
 * </ul>
 *
 * @see TelemetryBus
 * @see TelemetryScope
 */
public sealed interface TelemetryEvent permits
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
