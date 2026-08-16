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
package com.spectrayan.spector.commons.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpectorCacheKeyGeneratorTest {

    @Test
    @DisplayName("identity key generator leaves logical keys unchanged")
    void identity_leavesKeyUnchanged() {
        var keyGen = SpectorCacheKeyGenerator.identity();
        assertThat(keyGen.resolve("test-cache", "overview:50")).isEqualTo("overview:50");
    }

    @Test
    @DisplayName("forNamespace prefixes keys with ns:{namespaceId}:")
    void forNamespace_prefixesWithNamespace() {
        var keyGen = SpectorCacheKeyGenerator.forNamespace("user-12345");
        assertThat(keyGen.resolve("test-cache", "overview:50")).isEqualTo("ns:user-12345:overview:50");
        assertThat(keyGen.resolve("stats-cache", "current")).isEqualTo("ns:user-12345:current");
    }

    @Test
    @DisplayName("forNamespace with default, null, or blank falls back to identity")
    void forNamespace_fallbackToIdentity() {
        assertThat(SpectorCacheKeyGenerator.forNamespace("default").resolve("c", "k")).isEqualTo("k");
        assertThat(SpectorCacheKeyGenerator.forNamespace(null).resolve("c", "k")).isEqualTo("k");
        assertThat(SpectorCacheKeyGenerator.forNamespace("").resolve("c", "k")).isEqualTo("k");
        assertThat(SpectorCacheKeyGenerator.forNamespace("   ").resolve("c", "k")).isEqualTo("k");
    }
}
