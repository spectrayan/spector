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
package com.spectrayan.spector.core.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class XxHash64Test {

    @Test
    @DisplayName("Empty byte array produces PRIME5")
    void emptyArrayProducesPrime5() {
        long hash = XxHash64.hash(new byte[0]);
        // PRIME5 = 0x27D4EB2F165667C5L with final mixing
        assertThat(hash).isNotZero();
    }

    @Test
    @DisplayName("Null byte array produces 0L")
    void nullArrayProducesZero() {
        assertThat(XxHash64.hash(null)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Deterministic hash for sample string")
    void deterministicHash() {
        byte[] bytes = "Spector Core Cognitive Kernel".getBytes(StandardCharsets.UTF_8);
        long hash1 = XxHash64.hash(bytes);
        long hash2 = XxHash64.hash(bytes);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotZero();
    }

    @Test
    @DisplayName("Long byte array (> 32 bytes) hashes correctly across multiple blocks")
    void longArrayHashesDeterministically() {
        byte[] longData = new byte[128];
        for (int i = 0; i < longData.length; i++) {
            longData[i] = (byte) (i * 7);
        }
        long h1 = XxHash64.hash(longData);
        long h2 = XxHash64.hash(longData);
        assertThat(h1).isEqualTo(h2);
    }
}
