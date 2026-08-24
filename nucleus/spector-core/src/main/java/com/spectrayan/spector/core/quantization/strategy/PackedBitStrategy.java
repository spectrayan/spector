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
package com.spectrayan.spector.core.quantization.strategy;
import com.spectrayan.spector.commons.error.SpectorException;

import com.spectrayan.spector.core.quantization.CrumbPacker;
import com.spectrayan.spector.core.quantization.NibblePacker;
import com.spectrayan.spector.core.quantization.NonUniformQuantizer;
import com.spectrayan.spector.core.similarity.PackedDotProduct;
import com.spectrayan.spector.core.similarity.SimilarityFunction;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.function.BiFunction;

/**
 * Unified quantization strategy for sub-byte packed quantization (INT4 and INT2)
 * via {@link NonUniformQuantizer}.
 *
 * <p>This class replaces the former {@code Int4Strategy} and {@code Int2Strategy} which
 * were 95% identical, differing only in the packer, SIMD kernel, bytes-per-vector
 * calculation, and compression factor. Those four parameters are now injected
 * at construction time.</p>
 *
 * <h3>Zero-Copy Distance</h3>
 * <p>The {@link #distance} method passes the off-heap {@link MemorySegment} and offset
 * directly to the SIMD kernel ({@link PackedDotProduct}). Packed values are unpacked
 * inside the kernel — no intermediate {@code byte[]} copy in the hot path.</p>
 */
final class PackedBitStrategy implements QuantizationStrategy {

    /** Functional interface for bit packing (NibblePacker.pack / CrumbPacker.pack). */
    @FunctionalInterface
    interface Packer {
        byte[] pack(int[] values, int length);
    }

    /** Functional interface for bit unpacking (NibblePacker.unpack / CrumbPacker.unpack). */
    @FunctionalInterface
    interface Unpacker {
        int[] unpack(byte[] packed, int length);
    }

    /** Functional interface for the SIMD dot product kernel. */
    @FunctionalInterface
    interface PackedDotProductFn {
        float compute(float[] query, MemorySegment segment, long offset,
                      float[] globalCentroids, int dimensions);
    }

    private final NonUniformQuantizer quantizer;
    private final SimilarityFunction similarityFunction;
    private final float[] globalCentroids;
    private final int bpv;
    private final int compressionFactor;
    private final Packer packer;
    private final Unpacker unpacker;
    private final PackedDotProductFn dotProductFn;

    PackedBitStrategy(NonUniformQuantizer quantizer,
                      SimilarityFunction similarityFunction,
                      float[] globalCentroids,
                      int bpv,
                      int compressionFactor,
                      Packer packer,
                      Unpacker unpacker,
                      PackedDotProductFn dotProductFn) {
        this.quantizer = quantizer;
        this.similarityFunction = similarityFunction;
        this.globalCentroids = globalCentroids;
        this.bpv = bpv;
        this.compressionFactor = compressionFactor;
        this.packer = packer;
        this.unpacker = unpacker;
        this.dotProductFn = dotProductFn;
    }

    /**
     * Creates a PackedBitStrategy for 4-bit (INT4/nibble) quantization.
     */
    static PackedBitStrategy int4(NonUniformQuantizer quantizer,
                                   SimilarityFunction similarityFunction,
                                   float[] globalCentroids) {
        return new PackedBitStrategy(
                quantizer, similarityFunction, globalCentroids,
                (quantizer.dimensions() + 1) / 2,  // ceil(D/2)
                8,                                   // float32 (4 bytes) → INT4 (0.5 bytes)
                NibblePacker::pack,
                NibblePacker::unpack,
                PackedDotProduct::computeInt4);
    }

    /**
     * Creates a PackedBitStrategy for 2-bit (INT2/crumb) quantization.
     */
    static PackedBitStrategy int2(NonUniformQuantizer quantizer,
                                   SimilarityFunction similarityFunction,
                                   float[] globalCentroids) {
        return new PackedBitStrategy(
                quantizer, similarityFunction, globalCentroids,
                (quantizer.dimensions() + 3) / 4,  // ceil(D/4)
                16,                                  // float32 (4 bytes) → INT2 (0.25 bytes)
                CrumbPacker::pack,
                CrumbPacker::unpack,
                PackedDotProduct::computeInt2);
    }

    @Override
    public void encode(float[] vector, MemorySegment segment, long offset) {
        int[] levels = quantizer.encode(vector);
        byte[] packed = packer.pack(levels, quantizer.dimensions());
        MemorySegment.copy(packed, 0, segment, ValueLayout.JAVA_BYTE, offset, packed.length);
    }

    @Override
    public float[] decode(MemorySegment segment, long offset, int dimensions) {
        byte[] packed = new byte[bpv];
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset, packed, 0, bpv);
        int[] levels = unpacker.unpack(packed, dimensions);
        return quantizer.decode(levels);
    }

    /**
     * Computes packed asymmetric dot product — <b>zero-copy hot path</b>.
     *
     * <p>Passes the off-heap segment and offset directly to the SIMD kernel.
     * No {@code byte[]} is allocated — packed values are unpacked inside the
     * kernel reading directly from off-heap memory.</p>
     */
    @Override
    public float distance(MemorySegment segment, long offset, DistanceContext ctx) {
        if (!(ctx instanceof DistanceContext.PackedContext pc)) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "context", "expected PackedContext but got " + ctx.getClass().getSimpleName());
        }
        float dot = dotProductFn.compute(
                pc.query(), segment, offset, pc.globalCentroids(), pc.dimensions());
        return similarityFunction.higherIsBetter() ? dot : -dot;
    }

    @Override
    public DistanceContext prepareQueryContext(float[] query) {
        return new DistanceContext.PackedContext(query, globalCentroids, quantizer.dimensions());
    }

    @Override
    public int bytesPerVector() {
        return bpv;
    }

    @Override
    public int compressionFactor(int dimensions) {
        return compressionFactor;
    }
}
