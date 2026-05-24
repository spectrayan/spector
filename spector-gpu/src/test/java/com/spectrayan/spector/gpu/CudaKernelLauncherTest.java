package com.spectrayan.spector.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CudaKernelLauncher}.
 *
 * <p>Tests run regardless of CUDA availability —
 * they validate the API contract and error handling.</p>
 */
class CudaKernelLauncherTest {

    @Test
    void constructor_throwsWhenCudaUnavailable() {
        if (GpuCapability.isAvailable()) {
            // CUDA available — constructor should succeed and load PTX
            try (var launcher = new CudaKernelLauncher()) {
                assertNotNull(launcher);
            }
        } else {
            // CUDA unavailable — constructor should throw
            assertThrows(IllegalStateException.class, CudaKernelLauncher::new);
        }
    }

    @Test
    void batchCosine_emptyInput() {
        if (!GpuCapability.isAvailable()) return;

        try (var launcher = new CudaKernelLauncher()) {
            float[] result = launcher.batchCosine(new float[384], new float[0], 0, 384);
            assertNotNull(result);
            assertEquals(0, result.length);
        }
    }

    @Test
    void close_isIdempotent() {
        if (!GpuCapability.isAvailable()) return;

        var launcher = new CudaKernelLauncher();
        launcher.close();
        launcher.close(); // should not throw
    }
}
