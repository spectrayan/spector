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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.util.function.Function;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;

/**
 * Encapsulates point vector retrieval and scalar dequantization from partitioned off-heap memory.
 *
 * <p>Resolves memory ID via {@link MemoryIndex}, routes to the enclosing {@link PartitionHandle},
 * locates the off-heap {@link MemorySegment}, and dequantizes the vector payload using
 * the calibrated {@link ScalarQuantizer}.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>SRP</b>: Dedicated strictly to off-heap vector location and decoding.</li>
 *   <li><b>DIP</b>: Implements {@link Function Function&lt;String, float[]&gt;} for zero coupling with callers.</li>
 *   <li><b>Thread Safety</b>: Read-only operations over partitioned mmap segments; fully thread-safe.</li>
 * </ul>
 */
public final class CognitiveVectorAccessor implements Function<String, float[]> {

    private final MemoryIndex index;
    private final PartitionRegistry partitionRegistry;
    private final ScalarQuantizer quantizer;

    /**
     * Constructs a CognitiveVectorAccessor.
     *
     * @param index             memory index for ID-to-location resolution
     * @param partitionRegistry partition registry for partition router lookup
     * @param quantizer         calibrated scalar quantizer for INT8-to-FLOAT32 dequantization
     */
    public CognitiveVectorAccessor(MemoryIndex index,
                                   PartitionRegistry partitionRegistry,
                                   ScalarQuantizer quantizer) {
        this.index = Objects.requireNonNull(index, "index must not be null");
        this.partitionRegistry = Objects.requireNonNull(partitionRegistry, "partitionRegistry must not be null");
        this.quantizer = quantizer;
    }

    /**
     * Retrieves and dequantizes the float32 vector for a memory by ID.
     *
     * @param memoryId unique memory identifier
     * @return decoded float[] vector, or {@code null} if memory not found or quantizer uncalibrated
     */
    @Override
    public float[] apply(String memoryId) {
        if (memoryId == null || quantizer == null) {
            return null;
        }

        float[] mins = quantizer.mins();
        float[] scales = quantizer.scales();
        if (mins == null || scales == null) {
            return null;
        }

        MemoryIndex.MemoryLocation loc = index.locate(memoryId);
        if (loc == null) {
            return null;
        }

        CognitiveMemoryRouter router = partitionRegistry.routerFor(loc.colocatedPartition());
        if (router == null) {
            return null;
        }

        MemorySegment seg = router.segmentFor(loc.type());
        if (seg == null) {
            return null;
        }

        CognitiveRecordLayout layout = router.layoutFor(loc.type());
        if (layout == null) {
            return null;
        }

        long offset = layout.vectorOffset(loc.offset());
        int length = mins.length;
        float[] vec = new float[length];
        for (int i = 0; i < length; i++) {
            int q = Byte.toUnsignedInt(seg.get(ValueLayout.JAVA_BYTE, offset + i));
            vec[i] = mins[i] + (q / 255.0f) * scales[i];
        }
        return vec;
    }
}
