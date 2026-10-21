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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.PurgeResult;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Purge of a record that was already forgotten.
 *
 * <p>{@code forget} drops the record from the id index, and {@code purge} used to look the id up there only,
 * so the most natural erasure sequence (hide it first, destroy it later) reported "no memory found" and left
 * the text on disk. These tests pin the fix: purge finds the tombstoned record, erases its payload and text,
 * and says so in the receipt.</p>
 */
@DisplayName("purge after forget")
class PurgeAfterForgetTest {

    private static MemoryProperties props(FakeEmbeddingProvider embed) {
        return new MemoryProperties()
                .setDimensions(embed.dimensions())
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(50)
                .setProceduralCapacity(20);
    }

    private static SpectorMemory inMemory() {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        return DefaultSpectorMemory.builder(props(embed))
                .embeddingProvider(embed)
                .namespaceId("purge-after-forget")
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    private static SpectorMemory onDisk(Path dir) {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        return DefaultSpectorMemory.builder(props(embed))
                .embeddingProvider(embed)
                .namespaceId("purge-after-forget-disk")
                .persistence(dir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .build();
    }

    @Test
    @DisplayName("in memory: forget then purge finds the record and zeroes its payload")
    void forgetThenPurgeInMemory() {
        try (SpectorMemory memory = inMemory()) {
            memory.remember("fp", "Forgotten first, purged second.", MemoryType.SEMANTIC, "t");
            var impl = (DefaultSpectorMemory) memory;
            var loc = impl.index().locate("fp");
            assertThat(loc).isNotNull();

            assertThat(memory.forgetWithResult("fp").found()).isTrue();
            PurgeResult result = memory.purge("fp");

            assertThat(result.found()).as("a tombstoned record must still be purgeable").isTrue();
            assertThat(result.wasTombstoned()).isTrue();
            assertThat(result.payloadBytesZeroed()).isPositive();
            assertThat(result.textDestroyed()).isTrue();
            assertThat(result.purgedAt()).isNotNull();
            assertThat(impl.cognitiveRouter().isPurged(loc)).isTrue();

            // Idempotent: the second purge has nothing left to destroy.
            assertThat(memory.purge("fp").found()).isFalse();
        }
    }

    @Test
    @DisplayName("on disk: forget then purge erases the text bytes from every file under the store")
    void forgetThenPurgeOnDisk(@TempDir Path dir) throws IOException {
        String secret = "Forgotten-then-purged-secret-text-on-disk-7f3a";
        PurgeResult result;
        try (SpectorMemory memory = onDisk(dir)) {
            memory.remember("fp-disk", secret, MemoryType.SEMANTIC, "t");
            memory.remember("fp-keep", "Unrelated surviving content.", MemoryType.SEMANTIC, "t");
            memory.forget("fp-disk");
            result = memory.purge("fp-disk");
            assertThat(memory.inspect("fp-keep")).isNotNull();
        }

        assertThat(result.found()).isTrue();
        assertThat(result.wasTombstoned()).isTrue();
        assertThat(result.textBytesZeroed()).isEqualTo(secret.length());
        assertThat(result.textDestroyed()).isTrue();
        assertNotOnDisk(dir, secret);
    }

    @Test
    @DisplayName("on disk: a record forgotten before a restart can still be purged after it")
    void forgetRestartThenPurge(@TempDir Path dir) throws IOException {
        String secret = "Forgotten-before-restart-secret-text-91c2";
        try (SpectorMemory memory = onDisk(dir)) {
            memory.remember("fr-disk", secret, MemoryType.SEMANTIC, "t");
            memory.remember("fr-keep", "Unrelated surviving content after restart.", MemoryType.SEMANTIC, "t");
            memory.forget("fr-disk");
        }

        PurgeResult result;
        try (SpectorMemory memory = onDisk(dir)) {
            result = memory.purge("fr-disk");
            assertThat(memory.inspect("fr-keep")).isNotNull();
        }

        assertThat(result.found()).as("the tombstone must survive a restart").isTrue();
        assertThat(result.wasTombstoned()).isTrue();
        assertThat(result.textDestroyed()).isTrue();
        assertNotOnDisk(dir, secret);
    }

    @Test
    @DisplayName("after vacuum compacts a tombstone away, purge does not zero the record now in its slot")
    void purgeAfterVacuumLeavesNeighboursIntact(@TempDir Path dir) {
        try (SpectorMemory memory = onDisk(dir)) {
            memory.remember("vac-gone", "Forgotten then compacted by vacuum.", MemoryType.SEMANTIC, "t");
            memory.remember("vac-keep-1", "First survivor of the vacuum.", MemoryType.SEMANTIC, "t");
            memory.remember("vac-keep-2", "Second survivor of the vacuum.", MemoryType.SEMANTIC, "t");
            memory.forget("vac-gone");
            var impl = (DefaultSpectorMemory) memory;
            impl.vacuum(MemoryType.SEMANTIC);

            PurgeResult result = memory.purge("vac-gone");

            assertThat(result.found()).isTrue();
            assertThat(result.wasTombstoned()).isTrue();
            // The payload went with the compaction; the slot now belongs to a survivor and must not be zeroed.
            assertThat(result.payloadBytesZeroed()).isZero();
            for (String id : List.of("vac-keep-1", "vac-keep-2")) {
                var loc = impl.index().locate(id);
                assertThat(loc).as(id).isNotNull();
                assertThat(impl.cognitiveRouter().isPurged(loc)).as(id).isFalse();
                assertThat(impl.cognitiveRouter().readVector(loc)).as(id).isNotNull();
                assertThat(memory.inspect(id)).as(id).isNotNull();
            }
        }
    }

    private static void assertNotOnDisk(Path dir, String secret) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        assertThat(files).isNotEmpty();
        for (Path file : files) {
            String raw = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            assertThat(raw).as("purged text must not survive in %s", file).doesNotContain(secret);
        }
    }
}
