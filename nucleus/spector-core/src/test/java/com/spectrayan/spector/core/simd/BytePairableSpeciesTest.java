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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;

/**
 * Guards the float/byte species pairing the INT8 quantisation kernels depend on.
 *
 * <p>These tests exist because the kernels derived their byte species as
 * {@code VectorShape.forBitSize(floatLanes * Byte.SIZE)} straight from the platform's preferred float species.
 * That works on x86_64, where the preferred species has 8 lanes and the derived byte shape is a valid 64 bits.
 * On aarch64 the preferred species has 4 lanes, the derived shape is 32 bits, no such shape exists, and both
 * kernels threw during class initialisation — so every SVASQ path was unusable on Apple Silicon and Graviton
 * while CI, which runs x86_64, stayed green.</p>
 *
 * <p>Written to be meaningful on <em>any</em> host: they assert the pairing property rather than a specific
 * lane count, so they would have caught the defect when run on the affected platform and cannot be satisfied
 * by a value that happens to work on the CI runner.</p>
 */
@DisplayName("Byte-Pairable SIMD Species")
class BytePairableSpeciesTest {

    @Test
    @DisplayName("a byte species with a matching lane count can always be derived")
    void byteSpeciesIsAlwaysDerivable() {
        VectorSpecies<Float> floats = SimdCapability.BYTE_PAIRABLE_SPECIES;

        // The exact operation the kernels perform. It threw on aarch64 before the fix.
        assertThatCode(() -> VectorSpecies.of(byte.class,
                VectorShape.forBitSize(floats.length() * Byte.SIZE)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the derived byte species has the same lane count as the float species")
    void laneCountsMatch() {
        VectorSpecies<Float> floats = SimdCapability.BYTE_PAIRABLE_SPECIES;
        VectorSpecies<Byte> bytes = VectorSpecies.of(byte.class,
                VectorShape.forBitSize(floats.length() * Byte.SIZE));

        // Equal lane counts are what make castShape correct in the kernels; unequal counts would silently
        // score against the wrong elements rather than fail.
        assertThat(bytes.length()).isEqualTo(floats.length());
    }

    @Test
    @DisplayName("the float species is wide enough for the narrowest legal byte shape")
    void floatSpeciesMeetsTheMinimum() {
        // 64 bits is the narrowest shape the Vector API defines, so 8 byte lanes, so 8 float lanes.
        assertThat(SimdCapability.BYTE_PAIRABLE_SPECIES.length()).isGreaterThanOrEqualTo(64 / Byte.SIZE);
    }

    @Test
    @DisplayName("it never narrows the platform's preferred species")
    void neverNarrowsThePreferredSpecies() {
        // Widening where required is the fix; narrowing would throw away hardware the host does have.
        assertThat(SimdCapability.BYTE_PAIRABLE_SPECIES.length())
                .isGreaterThanOrEqualTo(SimdCapability.PREFERRED_SPECIES.length());
    }

    @Test
    @DisplayName("both INT8 kernels initialise on this host")
    void kernelsInitialise() {
        // A direct guard on the failure mode: these threw ExceptionInInitializerError on aarch64.
        assertThatCode(() -> {
            Class.forName("com.spectrayan.spector.core.quantization.svasq.SvasqSimdKernel");
            Class.forName("com.spectrayan.spector.core.quantization.svasq.Svasq4SimdKernel");
        }).doesNotThrowAnyException();
    }
}
