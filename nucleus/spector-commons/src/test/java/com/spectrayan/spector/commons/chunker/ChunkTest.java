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
package com.spectrayan.spector.commons.chunker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Chunk} verifying immutability, defensive copies,
 * and Valhalla / JEP 390 value-based class conformance.
 */
@DisplayName("Chunk Record & Value-Based Certification")
class ChunkTest {

    @Test
    @DisplayName("Chunk rejects null text")
    void rejectsNullText() {
        assertThatThrownBy(() -> new Chunk("doc-1", "chunk-1", 0, null, 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text must not be null");
    }

    @Test
    @DisplayName("Chunk defaults null metadata to empty immutable map")
    void defaultsNullMetadata() {
        Chunk chunk = new Chunk("doc-1", "chunk-1", 0, "hello", 0, 5, null);
        assertThat(chunk.metadata()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Chunk defensively copies metadata map")
    void defensivelyCopiesMetadata() {
        Map<String, String> mutableMap = new HashMap<>();
        mutableMap.put("key", "value1");

        Chunk chunk = new Chunk("doc-1", "chunk-1", 0, "sample text", 0, 11, mutableMap);
        mutableMap.put("key", "value2");

        assertThat(chunk.metadata().get("key")).isEqualTo("value1");
        assertThatThrownBy(() -> chunk.metadata().put("newKey", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Convenience constructor without metadata")
    void convenienceConstructor() {
        Chunk chunk = new Chunk("doc-1", "c-0", 0, "body text", 10, 19);
        assertThat(chunk.parentId()).isEqualTo("doc-1");
        assertThat(chunk.chunkId()).isEqualTo("c-0");
        assertThat(chunk.index()).isZero();
        assertThat(chunk.text()).isEqualTo("body text");
        assertThat(chunk.length()).isEqualTo(9);
        assertThat(chunk.metadata()).isEmpty();
    }

    @Test
    @DisplayName("Valhalla / JEP 390 value-based class certification")
    void valhallaValueBasedCertification() {
        assertThat(Chunk.class.isRecord()).isTrue();

        Map<String, String> meta = Map.of("type", "heading", "level", "2");
        Chunk a = new Chunk("doc-1", "c-0", 0, "Title", 0, 5, meta);
        Chunk b = new Chunk("doc-1", "c-0", 0, "Title", 0, 5, meta);

        assertThat(a).isNotSameAs(b);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).isEqualTo(b.toString());

        Chunk different = new Chunk("doc-1", "c-1", 1, "Title", 0, 5, meta);
        assertThat(a).isNotEqualTo(different);
    }
}
