/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.cortex;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.junit.jupiter.api.Test;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.model.MemoryType;

class CognitiveVectorAccessorTest {

    @Test
    void testNullInputs() {
        MemoryIndex index = mock(MemoryIndex.class);
        PartitionRegistry registry = mock(PartitionRegistry.class);
        ScalarQuantizer quantizer = mock(ScalarQuantizer.class);

        CognitiveVectorAccessor accessor = new CognitiveVectorAccessor(index, registry, quantizer);

        assertNull(accessor.apply(null));

        when(index.locate("missing-id")).thenReturn(null);
        assertNull(accessor.apply("missing-id"));
    }

    @Test
    void testUncalibratedQuantizer() {
        MemoryIndex index = mock(MemoryIndex.class);
        PartitionRegistry registry = mock(PartitionRegistry.class);
        ScalarQuantizer quantizer = mock(ScalarQuantizer.class);
        when(quantizer.mins()).thenReturn(null);

        CognitiveVectorAccessor accessor = new CognitiveVectorAccessor(index, registry, quantizer);
        assertNull(accessor.apply("any-id"));
    }

    @Test
    void testSuccessfulVectorRetrievalAndDequantization() {
        MemoryIndex index = mock(MemoryIndex.class);
        PartitionRegistry registry = mock(PartitionRegistry.class);
        CognitiveMemoryRouter router = mock(CognitiveMemoryRouter.class);
        CognitiveRecordLayout layout = mock(CognitiveRecordLayout.class);
        ScalarQuantizer quantizer = mock(ScalarQuantizer.class);

        float[] mins = new float[]{0.0f, -1.0f};
        float[] scales = new float[]{2.0f, 4.0f};
        when(quantizer.mins()).thenReturn(mins);
        when(quantizer.scales()).thenReturn(scales);

        MemoryIndex.MemoryLocation loc = new MemoryIndex.MemoryLocation(MemoryType.EPISODIC, 100L, 0);
        when(index.locate("mem-123")).thenReturn(loc);
        when(registry.routerFor(0)).thenReturn(router);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(256);
            long vectorOffset = 64L;
            when(router.segmentFor(MemoryType.EPISODIC)).thenReturn(segment);
            when(router.layoutFor(MemoryType.EPISODIC)).thenReturn(layout);
            when(layout.vectorOffset(100L)).thenReturn(vectorOffset);

            // Write test bytes at vectorOffset: byte 0 = 128 (~0.5), byte 1 = 255 (1.0)
            segment.set(ValueLayout.JAVA_BYTE, vectorOffset, (byte) 128);
            segment.set(ValueLayout.JAVA_BYTE, vectorOffset + 1, (byte) -1); // 255 unsigned

            CognitiveVectorAccessor accessor = new CognitiveVectorAccessor(index, registry, quantizer);
            float[] result = accessor.apply("mem-123");

            assertNotNull(result);
            assertEquals(2, result.length);
            // dim 0: 0.0 + (128 / 255.0f) * 2.0 ≈ 1.0039f
            assertEquals(0.0f + (128 / 255.0f) * 2.0f, result[0], 0.001f);
            // dim 1: -1.0 + (255 / 255.0f) * 4.0 = 3.0f
            assertEquals(3.0f, result[1], 0.001f);
        }
    }
}
