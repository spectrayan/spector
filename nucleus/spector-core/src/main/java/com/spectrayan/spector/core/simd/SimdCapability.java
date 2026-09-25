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
package com.spectrayan.spector.core.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * Reports the SIMD capabilities detected at runtime.
 *
 * <p>This class queries the JVM for the preferred {@link VectorSpecies}
 * and provides diagnostic information about the available SIMD width
 * and instruction set architecture.</p>
 */
public final class SimdCapability {

    /** The preferred float vector species for this platform (AVX2 = 256-bit, AVX-512 = 512-bit, etc.). */
    public static final VectorSpecies<Float> PREFERRED_SPECIES = FloatVector.SPECIES_PREFERRED;

    /**
     * Fewest float lanes usable by a kernel that pairs a float species with a same-lane-count byte species.
     *
     * <p>The narrowest shape the Vector API defines is 64-bit, so a byte species has at least 8 lanes.</p>
     */
    private static final int MIN_BYTE_PAIRABLE_LANES = 64 / Byte.SIZE;

    /**
     * Float species for kernels that must pair each float lane with a byte lane.
     *
     * <p>Equal to {@link #PREFERRED_SPECIES} wherever that species has at least
     * {@value #MIN_BYTE_PAIRABLE_LANES} lanes, and widened to exactly that many otherwise.</p>
     *
     * <h3>Why this exists</h3>
     * <p>The INT8 quantisation kernels derive their byte species as
     * {@code VectorShape.forBitSize(floatLanes * Byte.SIZE)}. On x86_64 the preferred float species has 8
     * lanes, which asks for a valid 64-bit byte shape. On aarch64 the 128-bit NEON registers give a 4-lane
     * preferred species, which asks for a 32-bit shape that does not exist — so those kernels threw during
     * class initialisation and were entirely unusable on Apple Silicon and Graviton. CI runs x86_64, so the
     * failure never surfaced there: the build was green on a platform where the bug cannot occur.</p>
     *
     * <p>Where the returned species is wider than the hardware register, the Vector API emulates it across
     * multiple registers. That costs throughput and is the right trade against not running at all; it also
     * keeps a single code path rather than a second narrow-lane kernel to maintain and test.</p>
     */
    public static final VectorSpecies<Float> BYTE_PAIRABLE_SPECIES = resolveBytePairableSpecies();

    private SimdCapability() {
        // utility class
    }

    private static VectorSpecies<Float> resolveBytePairableSpecies() {
        if (PREFERRED_SPECIES.length() >= MIN_BYTE_PAIRABLE_LANES) {
            return PREFERRED_SPECIES;
        }
        return VectorSpecies.of(float.class,
                jdk.incubator.vector.VectorShape.forBitSize(MIN_BYTE_PAIRABLE_LANES * Float.SIZE));
    }

    /**
     * Returns the number of float lanes in a single SIMD register.
     *
     * @return lane count (e.g. 8 for AVX2, 16 for AVX-512)
     */
    public static int laneCount() {
        return PREFERRED_SPECIES.length();
    }

    /**
     * Returns the SIMD vector bit width.
     *
     * @return bit width (e.g. 256 for AVX2, 512 for AVX-512)
     */
    public static int vectorBitSize() {
        return PREFERRED_SPECIES.vectorBitSize();
    }

    /**
     * Returns a human-readable summary of SIMD capabilities.
     *
     * @return capability report string
     */
    public static String report() {
        return String.format(
                "SIMD Capability: species=%s, lanes=%d, bitSize=%d",
                PREFERRED_SPECIES, laneCount(), vectorBitSize()
        );
    }

    /**
     * Checks if the Vector API ({@code jdk.incubator.vector}) is available and initialized.
     *
     * @return true if the Vector API preferred species is available and non-empty
     */
    public static boolean isVectorApiAvailable() {
        try {
            return PREFERRED_SPECIES != null && PREFERRED_SPECIES.length() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Preflight validation assertion that the Vector API is available.
     *
     * @throws IllegalStateException if the Vector API is unavailable
     */
    public static void checkVectorApiPreflight() {
        if (!isVectorApiAvailable()) {
            throw new IllegalStateException(
                    "SIMD Vector API (jdk.incubator.vector) is unavailable on this JVM. " +
                    "Ensure JVM is launched with --enable-preview and --add-modules=jdk.incubator.vector."
            );
        }
    }
}
