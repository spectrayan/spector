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

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the honesty of the {@code forget} and {@code vacuum} result surfaces (#983).
 *
 * <p>Both reported outcomes they had not achieved. {@code forget} returned normally when the id was absent
 * and the MCP tool said the memory "has been forgotten", so a typo produced a confident false confirmation
 * on a deletion path. {@code vacuum} returned {@code bytesReclaimed = tombstoneCount * recordStride} — a
 * multiplication presented as a measurement — and logged "reclaimed {}KB" for an operation that performed
 * no write at all.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/983">spectrayan/spector#983</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("forget and vacuum report real outcomes (#983)")
class ForgetAndVacuumHonestyTest {

    private SpectorMemory memory;

    @BeforeAll
    void initMemory(@TempDir Path tempDir) {
        FakeEmbeddingProvider embedProvider = new FakeEmbeddingProvider();
        var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(embedProvider.dimensions())
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(50)
                .setProceduralCapacity(20);
        memory = DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(embedProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    @AfterAll
    void shutdown() {
        if (memory != null) memory.close();
    }

    @Nested
    @DisplayName("forget")
    class Forget {

        @Test
        @DisplayName("reports found=true and actually tombstones an existing memory")
        void reportsFoundForExistingMemory() {
            memory.remember("fg-exists", "A memory that will be forgotten.", MemoryType.SEMANTIC, "test");

            var result = memory.forgetWithResult("fg-exists");

            assertThat(result.found()).isTrue();
            assertThat(result.id()).isEqualTo("fg-exists");
            assertThat(result.tombstonedAtEpochMs()).isPositive();
            assertThat(memory.inspect("fg-exists"))
                    .as("tombstoned records are invisible to inspect")
                    .isNull();
        }

        @Test
        @DisplayName("reports found=false for an unknown id instead of claiming success")
        void reportsNotFoundForUnknownId() {
            var result = memory.forgetWithResult("fg-never-existed");

            // This is the assertion that matters: before #983 the caller could not distinguish this from a
            // real deletion, and the MCP tool reported "has been forgotten (tombstoned)" regardless.
            assertThat(result.found()).isFalse();
            assertThat(result.tombstonedAtEpochMs()).isZero();
        }

        @Test
        @DisplayName("stays idempotent — a second forget does not throw")
        void remainsIdempotent() {
            memory.remember("fg-twice", "Forgotten twice.", MemoryType.SEMANTIC, "test");

            // Idempotency is a contract Spring AI's VectorStore delete and both chat-memory bulk paths
            // rely on, which is why the not-found case returns a result rather than throwing.
            assertThatCode(() -> {
                memory.forget("fg-twice");
                memory.forget("fg-twice");
            }).doesNotThrowAnyException();

            assertThat(memory.forgetWithResult("fg-twice").found())
                    .as("the second call reports honestly that there was nothing left to forget")
                    .isFalse();
        }

        @Test
        @DisplayName("the void overload still works for callers that do not care")
        void voidOverloadStillWorks() {
            memory.remember("fg-void", "Forgotten via void overload.", MemoryType.SEMANTIC, "test");
            assertThatCode(() -> memory.forget("fg-void")).doesNotThrowAnyException();
            assertThat(memory.inspect("fg-void")).isNull();
        }
    }

    @Nested
    @DisplayName("vacuum")
    class Vacuum {

        @Test
        @DisplayName("reports zero reclaimed bytes and compacted=false, not a computed estimate")
        void reportsZeroReclaimedAndNotCompacted() {
            for (int i = 0; i < 6; i++) {
                memory.remember("vac-" + i, "Vacuum subject " + i, MemoryType.SEMANTIC, "test");
            }
            for (int i = 0; i < 3; i++) {
                memory.forget("vac-" + i);
            }

            var result = memory.admin().vacuum(MemoryType.SEMANTIC);
            assertThat(result).as("tombstones exist, so a census is returned").isNotNull();

            // Asserting the specific values, not non-nullness: a test asserting "result != null" would have
            // passed against the fabricated tombstoneCount * stride figure.
            assertThat(result.bytesReclaimed())
                    .as("nothing is reclaimed, so no byte count may be reported")
                    .isZero();
            assertThat(result.compacted())
                    .as("the caller must be able to tell a census from a compaction")
                    .isFalse();
            assertThat(result.tombstonesRemoved())
                    .as("tombstones are found, not removed")
                    .isPositive();
        }

        @Test
        @DisplayName("the census factory never produces a non-zero byte count")
        void censusFactoryIsAlwaysZeroBytes() {
            var census = com.spectrayan.spector.memory.sync.CompactionResult.census(
                    MemoryType.EPISODIC, 1_000, 600, 400, 12L);

            assertThat(census.bytesReclaimed()).isZero();
            assertThat(census.compacted()).isFalse();
            assertThat(census.tombstonesRemoved()).isEqualTo(400);
        }
    }
}
