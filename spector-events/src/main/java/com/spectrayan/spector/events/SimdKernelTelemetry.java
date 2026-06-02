/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.spectrayan.spector.events;

/**
 * SIMD kernel execution telemetry — emitted per-query from SIMD-accelerated
 * distance computation kernels.
 *
 * @param kernelName       the SIMD kernel class (e.g., "CosineSimilarity")
 * @param laneWidth        SIMD lane width in floats (4, 8, 16)
 * @param vectorsProcessed number of vectors processed in this invocation
 * @param durationNanos    elapsed time in nanoseconds
 */
public record SimdKernelTelemetry(
        String kernelName,
        int laneWidth,
        int vectorsProcessed,
        long durationNanos
) implements TelemetryEvent {}
