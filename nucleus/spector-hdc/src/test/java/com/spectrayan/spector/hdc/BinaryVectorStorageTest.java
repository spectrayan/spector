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
package com.spectrayan.spector.hdc;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BinaryVectorStorageTest {

    @Test
    void testStoreAndRetrieveRoundtrip() {
        int dims = 1000;
        try (BinaryVectorStorage storage = new BinaryVectorStorage(10, dims)) {
            Hypervector v = Hypervector.random(dims, 42L);
            storage.putVector(5, v);
            Hypervector retrieved = storage.getVector(5);
            assertThat(retrieved).isEqualTo(v);
        }
    }

    @Test
    void testIndexBoundsValidation() {
        int dims = 1000;
        try (BinaryVectorStorage storage = new BinaryVectorStorage(5, dims)) {
            Hypervector v = Hypervector.random(dims, 42L);
            assertThatThrownBy(() -> storage.putVector(5, v))
                .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> storage.getVector(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Test
    void testAutoCloseableLifecycle() {
        BinaryVectorStorage storage = new BinaryVectorStorage(5, 1000);
        storage.close();
        // Accessing after close should throw exception depending on Arena implementation
        // FFM throws IllegalStateException when accessing closed arena
        assertThatThrownBy(() -> storage.getVector(0))
            .isInstanceOf(IllegalStateException.class);
    }
}
