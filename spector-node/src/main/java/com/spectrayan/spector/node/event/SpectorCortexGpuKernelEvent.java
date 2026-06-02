package com.spectrayan.spector.node.event;

import java.time.Instant;

/**
 * Cortex GPU kernel event — emitted after each CUDA kernel execution.
 * Used by the GPU timeline panel.
 *
 * @param nodeId              node that executed the kernel
 * @param timestamp           when the event was generated
 * @param streamIndex         CUDA stream index (for multi-stream visualization)
 * @param kernelName          kernel function name (e.g., "batch_cosine")
 * @param durationMicros      wall-clock kernel duration in microseconds
 * @param gridDimX            grid dimension X
 * @param gridDimY            grid dimension Y
 * @param gridDimZ            grid dimension Z
 * @param blockDimX           block dimension X
 * @param blockDimY           block dimension Y
 * @param blockDimZ           block dimension Z
 * @param memoryTransferBytes total host↔device memory transfer in bytes
 */
public record SpectorCortexGpuKernelEvent(
        String nodeId, Instant timestamp,
        int streamIndex, String kernelName,
        long durationMicros,
        int gridDimX, int gridDimY, int gridDimZ,
        int blockDimX, int blockDimY, int blockDimZ,
        long memoryTransferBytes
) implements SpectorEvent {
    @Override public String eventType() { return "cortex.gpu.kernel"; }
}
