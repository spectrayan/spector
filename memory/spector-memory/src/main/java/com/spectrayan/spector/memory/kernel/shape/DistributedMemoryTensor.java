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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.simd.RandomFeatureProjector;
import com.spectrayan.spector.core.similarity.DotProduct;
import com.spectrayan.spector.memory.kernel.MemoryShape;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Off-heap distributed holographic memory tensor backed by Panama FFM (Foreign Function &amp; Memory).
 *
 * <h3>Biological Analog: Pribram's Holonomic Brain Hologram</h3>
 * <p>Stores the linear superposition accumulator tensor \(\mathbf{T} \in \mathbb{R}^Y\) representing
 * all memories stored in the agent's lifetime. Evaluates global associative energy and prior epistemic
 * density across millions of memories in constant \(\mathcal{O}(Y)\) time without scanning individual
 * memory records.</p>
 *
 * <h3>Memory Layout (Header 64 Bytes + Y Floats)</h3>
 * <pre>
 *   [0..7]:   MAGIC (0x53504543544F524C)
 *   [8..15]:  VERSION (1)
 *   [16..23]: INPUT_DIM D (e.g. 768)
 *   [24..31]: FEATURE_DIM Y (e.g. 2048)
 *   [32..39]: PATTERN_COUNT K (long)
 *   [40..47]: SEED (long)
 *   [48..63]: RESERVED (16 bytes)
 *   [64..]:   TENSOR DATA (Y * 4 bytes)
 * </pre>
 *
 * @since 1.3.0
 */
public final class DistributedMemoryTensor implements AutoCloseable {

    public static final long MAGIC = 0x53504543544F524CL; // "SPECTORL"
    public static final long VERSION = 1L;
    public static final long HEADER_BYTES = 64L;

    private static final long OFFSET_MAGIC = 0L;
    private static final long OFFSET_VERSION = 8L;
    private static final long OFFSET_INPUT_DIM = 16L;
    private static final long OFFSET_FEATURE_DIM = 24L;
    private static final long OFFSET_PATTERN_COUNT = 32L;
    private static final long OFFSET_SEED = 40L;
    private static final long OFFSET_DATA = 64L;

    private final int inputDimension;
    private final int featureDimension;
    private final RandomFeatureProjector projector;
    private final Arena arena;
    private final MemorySegment segment;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Constructs a transient off-heap DistributedMemoryTensor with default feature dimension (2048).
     *
     * @param inputDimension input vector dimension D
     */
    public DistributedMemoryTensor(int inputDimension) {
        this(inputDimension, RandomFeatureProjector.DEFAULT_FEATURE_DIM, RandomFeatureProjector.DEFAULT_SEED);
    }

    /**
     * Constructs a transient off-heap DistributedMemoryTensor with custom dimensions and seed.
     *
     * @param inputDimension input vector dimension D
     * @param featureDimension random feature projection dimension Y
     * @param seed deterministic projection matrix seed
     */
    public DistributedMemoryTensor(int inputDimension, int featureDimension, long seed) {
        if (inputDimension <= 0 || featureDimension <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimensions must be positive");
        }
        this.inputDimension = inputDimension;
        this.featureDimension = featureDimension;
        this.projector = new RandomFeatureProjector(inputDimension, featureDimension, seed);
        this.arena = Arena.ofShared();

        long totalBytes = HEADER_BYTES + ((long) featureDimension * ValueLayout.JAVA_FLOAT.byteSize());
        this.segment = arena.allocate(totalBytes, 64); // 64-byte aligned

        // Initialize header
        segment.set(ValueLayout.JAVA_LONG, OFFSET_MAGIC, MAGIC);
        segment.set(ValueLayout.JAVA_LONG, OFFSET_VERSION, VERSION);
        segment.set(ValueLayout.JAVA_LONG, OFFSET_INPUT_DIM, inputDimension);
        segment.set(ValueLayout.JAVA_LONG, OFFSET_FEATURE_DIM, featureDimension);
        segment.set(ValueLayout.JAVA_LONG, OFFSET_PATTERN_COUNT, 0L);
        segment.set(ValueLayout.JAVA_LONG, OFFSET_SEED, seed);

        // Zero tensor data
        segment.asSlice(OFFSET_DATA, (long) featureDimension * 4).fill((byte) 0);
    }

    /**
     * Accumulates a memory vector into the global holographic tensor:
     * T <- T + Phi(vector)
     *
     * @param vector memory vector in R^D
     * @param beta inverse temperature
     */
    public void accumulate(float[] vector, float beta) {
        if (vector == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector must not be null");
        }
        float[] feat = new float[featureDimension];
        projector.project(vector, beta, feat);

        lock.lock();
        try {
            long dataOffset = OFFSET_DATA;
            for (int i = 0; i < featureDimension; i++) {
                long offset = dataOffset + ((long) i * 4);
                float cur = segment.get(ValueLayout.JAVA_FLOAT, offset);
                segment.set(ValueLayout.JAVA_FLOAT, offset, cur + feat[i]);
            }
            long count = segment.get(ValueLayout.JAVA_LONG, OFFSET_PATTERN_COUNT);
            segment.set(ValueLayout.JAVA_LONG, OFFSET_PATTERN_COUNT, count + 1);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retracts an evicted or forgotten memory vector from the holographic tensor:
     * T <- T - Phi(vector)
     *
     * @param vector memory vector in R^D
     * @param beta inverse temperature
     */
    public void retract(float[] vector, float beta) {
        if (vector == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector must not be null");
        }
        float[] feat = new float[featureDimension];
        projector.project(vector, beta, feat);

        lock.lock();
        try {
            long dataOffset = OFFSET_DATA;
            for (int i = 0; i < featureDimension; i++) {
                long offset = dataOffset + ((long) i * 4);
                float cur = segment.get(ValueLayout.JAVA_FLOAT, offset);
                segment.set(ValueLayout.JAVA_FLOAT, offset, Math.max(0.0f, cur - feat[i]));
            }
            long count = segment.get(ValueLayout.JAVA_LONG, OFFSET_PATTERN_COUNT);
            if (count > 0) {
                segment.set(ValueLayout.JAVA_LONG, OFFSET_PATTERN_COUNT, count - 1);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Multiplies the entire tensor accumulator by a decay factor (e.g. during sleep consolidation):
     * T <- factor * T
     *
     * @param factor decay multiplier in [0.0, 1.0]
     */
    public void decay(float factor) {
        lock.lock();
        try {
            long dataOffset = OFFSET_DATA;
            for (int i = 0; i < featureDimension; i++) {
                long offset = dataOffset + ((long) i * 4);
                float cur = segment.get(ValueLayout.JAVA_FLOAT, offset);
                segment.set(ValueLayout.JAVA_FLOAT, offset, cur * factor);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Evaluates the scalar holographic energy for a query vector in constant O(Y) time:
     * E(v; T) = -ln( <Phi(v), T> )
     *
     * @param queryVector query vector in R^D
     * @param beta inverse temperature
     * @return scalar associative energy (Float.POSITIVE_INFINITY if accumulator is zero)
     */
    public float evaluateEnergy(float[] queryVector, float beta) {
        if (queryVector == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Query vector must not be null");
        }
        float[] feat = new float[featureDimension];
        projector.project(queryVector, beta, feat);

        float dot = 0.0f;
        lock.lock();
        try {
            long dataOffset = OFFSET_DATA;
            for (int i = 0; i < featureDimension; i++) {
                float tVal = segment.get(ValueLayout.JAVA_FLOAT, dataOffset + ((long) i * 4));
                dot += feat[i] * tVal;
            }
        } finally {
            lock.unlock();
        }

        return dot > 0.0f ? -(float) Math.log(dot) : Float.POSITIVE_INFINITY;
    }

    /**
     * Returns a snapshot copy of the holographic accumulator tensor T in R^Y.
     *
     * @return float array of length Y
     */
    public float[] snapshotTensor() {
        float[] snapshot = new float[featureDimension];
        lock.lock();
        try {
            MemorySegment.copy(segment, ValueLayout.JAVA_FLOAT, OFFSET_DATA, snapshot, 0, featureDimension);
        } finally {
            lock.unlock();
        }
        return snapshot;
    }

    /**
     * Returns the number of patterns currently accumulated in this tensor.
     */
    public long patternCount() {
        lock.lock();
        try {
            return segment.get(ValueLayout.JAVA_LONG, OFFSET_PATTERN_COUNT);
        } finally {
            lock.unlock();
        }
    }

    public MemoryShape shape() {
        return MemoryShape.HOLOGRAPHIC;
    }

    public int inputDimension() {
        return inputDimension;
    }

    public int featureDimension() {
        return featureDimension;
    }

    @Override
    public void close() {
        if (arena.scope().isAlive()) {
            arena.close();
        }
    }
}
