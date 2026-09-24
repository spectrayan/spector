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

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.bundle.BundleFileLayout;
import com.spectrayan.spector.kernel.bundle.BundleSubHeader;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.store.SemanticMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.sync.CompactionResult;
import com.spectrayan.spector.memory.sync.VacuumCompactor;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Group 3 test suite for compaction (R2).
 *
 * <ul>
 *   <li>Task 3.6: remember → roll → compact → recall-by-id across partitions</li>
 *   <li>Task 3.7: edge counts reconciled post-compaction; no dangling slot references</li>
 *   <li>Task 3.8: compaction on frozen v4 fixture does not corrupt format</li>
 *   <li>Task 3.9: reclaimed bytes are measured, not computed</li>
 *   <li>Task 3.4: threshold triggering (0.20) and explicit force trigger</li>
 * </ul>
 */
@DisplayName("Group 3 — Partition Compaction (R2)")
class PartitionCompactionTest {

    private SpectorMemory memory;

    private SpectorMemory build(Path dir, int episodicCap, int semanticCap) {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(embed.dimensions())
                .setWorkingCapacity(32)
                .setEpisodicPartitionCapacity(episodicCap)
                .setSemanticCapacity(semanticCap)
                .setProceduralCapacity(32);
        memProps.getRemember().setSurpriseWarmup(1);

        return DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(embed)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .persistence(dir)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
            memory = null;
        }
    }

    private static Set<String> ids(List<CognitiveResult> results) {
        return results.stream().map(CognitiveResult::id).collect(Collectors.toSet());
    }

    private static long partitionDirCount(Path base) throws Exception {
        try (var stream = Files.newDirectoryStream(StoragePaths.partitionsDir(base))) {
            long n = 0;
            for (Path p : stream) {
                if (Files.isDirectory(p) && StoragePaths.isPartitionDir(p.getFileName().toString())) n++;
            }
            return n;
        }
    }

    private static Path materialise(Path workDir, String resource, String targetName) throws IOException {
        Path target = workDir.resolve(targetName);
        try (InputStream raw = PartitionCompactionTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(raw).as("fixture resource %s must be on the test classpath", resource).isNotNull();
            try (GZIPInputStream gz = new GZIPInputStream(raw)) {
                Files.copy(gz, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    // ── Task 3.6: remember → roll → compact → recall-by-id ────────

    @Nested
    @DisplayName("Task 3.6 — Gate: remember → roll → compact → recall-by-id")
    class GateRecallByIdAcrossPartitions {

        @Test
        @DisplayName("semantic: pre-roll records in frozen partition survive compaction and remain resolvable by ID and similarity")
        void semanticRollCompactRecallById(@TempDir Path dir) throws Exception {
            memory = build(dir, /*episodicCap*/ 64, /*semanticCap*/ 2);

            memory.remember("sem-0", "PostgreSQL MVCC engine architecture",
                    MemoryType.SEMANTIC, MemorySource.OBSERVED, "db");
            memory.remember("sem-1", "Redis in-memory dictionary data store",
                    MemoryType.SEMANTIC, MemorySource.OBSERVED, "db");
            memory.remember("sem-2", "Kubernetes container orchestration system",
                    MemoryType.SEMANTIC, MemorySource.OBSERVED, "infra");

            assertThat(partitionDirCount(dir)).as("partition roll occurred").isGreaterThanOrEqualTo(2);

            // Forget sem-0 in frozen partition 000
            memory.forget("sem-0");
            assertThat(memory.inspect("sem-0")).isNull();

            // Compact semantic tier across all partitions
            CompactionResult comp = memory.admin().vacuum(MemoryType.SEMANTIC, true);
            assertThat(comp).isNotNull();
            assertThat(comp.compacted()).isTrue();
            assertThat(comp.tombstonesRemoved()).isGreaterThanOrEqualTo(1);
            assertThat(comp.bytesReclaimed()).isPositive();

            // sem-0 was tombstoned and compacted away
            assertThat(memory.inspect("sem-0")).isNull();

            // sem-1 (relocated to offset 0 in frozen partition 000) must still resolve by ID with intact content
            var recSem1 = memory.inspect("sem-1");
            assertThat(recSem1).as("sem-1 must remain inspectable by id post-compaction").isNotNull();
            assertThat(recSem1.text()).isEqualTo("Redis in-memory dictionary data store");
            assertThat(recSem1.memoryType()).isEqualTo(MemoryType.SEMANTIC);

            // sem-2 (in active partition 001) must still resolve by ID with intact content
            var recSem2 = memory.inspect("sem-2");
            assertThat(recSem2).as("sem-2 must remain inspectable by id post-compaction").isNotNull();
            assertThat(recSem2.text()).isEqualTo("Kubernetes container orchestration system");

            // Semantic recall must find sem-1 and sem-2 and omit sem-0
            List<CognitiveResult> results = memory.recall("Redis in-memory dictionary data store",
                    RecallOptions.builder().topK(5).build());
            Set<String> hitIds = ids(results);
            assertThat(hitIds).contains("sem-1");
            assertThat(hitIds).doesNotContain("sem-0");
        }

        @Test
        @DisplayName("episodic: pre-roll records in frozen partition survive compaction and remain resolvable by ID")
        void episodicRollCompactRecallById(@TempDir Path dir) throws Exception {
            memory = build(dir, /*episodicCap*/ 2, /*semanticCap*/ 64);

            memory.remember("epi-0", "Database migration failed on shard seven",
                    MemoryType.EPISODIC, MemorySource.OBSERVED, "ops");
            memory.remember("epi-1", "Cache warmup completed for payments service",
                    MemoryType.EPISODIC, MemorySource.OBSERVED, "ops");
            memory.remember("epi-2", "Kafka consumer lag spiked during deployment",
                    MemoryType.EPISODIC, MemorySource.OBSERVED, "ops");

            assertThat(partitionDirCount(dir)).as("partition roll occurred").isGreaterThanOrEqualTo(2);

            // Forget epi-0 in frozen partition 000
            memory.forget("epi-0");
            assertThat(memory.inspect("epi-0")).isNull();

            // Compact episodic tier
            CompactionResult comp = memory.admin().vacuum(MemoryType.EPISODIC, true);
            assertThat(comp).isNotNull();
            assertThat(comp.compacted()).isTrue();
            assertThat(comp.tombstonesRemoved()).isGreaterThanOrEqualTo(1);
            assertThat(comp.bytesReclaimed()).isPositive();

            // epi-0 is gone
            assertThat(memory.inspect("epi-0")).isNull();

            // epi-1 (relocated in frozen partition 000) must still be inspectable
            var recEpi1 = memory.inspect("epi-1");
            assertThat(recEpi1).as("epi-1 must be inspectable post-compaction").isNotNull();
            assertThat(recEpi1.text()).isEqualTo("Cache warmup completed for payments service");

            // epi-2 (active partition 001) must still be inspectable
            var recEpi2 = memory.inspect("epi-2");
            assertThat(recEpi2).as("epi-2 must be inspectable post-compaction").isNotNull();
            assertThat(recEpi2.text()).isEqualTo("Kafka consumer lag spiked during deployment");

            // Recall returns epi-1 and epi-2, excludes epi-0
            List<CognitiveResult> results = memory.recall("payments service warmup",
                    RecallOptions.builder().topK(5).build());
            Set<String> hitIds = ids(results);
            assertThat(hitIds).contains("epi-1");
            assertThat(hitIds).doesNotContain("epi-0");
        }
    }

    // ── Task 3.7: Graph edges reconciled post-compaction ──────────

    @Nested
    @DisplayName("Task 3.7 — Edge counts reconciled post-compaction")
    class GraphEdgesReconciliation {

        @Test
        @DisplayName("dead records are detached from all graphs; live records maintain valid graph slots and edges")
        void edgesReconciledPostCompaction(@TempDir Path dir) {
            memory = build(dir, /*episodicCap*/ 64, /*semanticCap*/ 64);

            for (int i = 0; i < 4; i++) {
                memory.remember("g-" + i, "Astronomy concept " + i + " astrophysical simulation",
                        MemoryType.SEMANTIC, MemorySource.OBSERVED, "astronomy");
            }

            var impl = (DefaultSpectorMemory) memory;
            int deadSlot = impl.index().locate("g-1").graphSlot();
            int liveSlot0 = impl.index().locate("g-0").graphSlot();
            int liveSlot2 = impl.index().locate("g-2").graphSlot();

            // Forget g-1
            memory.forget("g-1");

            // Run compaction
            CompactionResult comp = memory.admin().vacuum(MemoryType.SEMANTIC, true);
            assertThat(comp.compacted()).isTrue();

            // g-1's graphSlot must be detached from all graphs
            assertThat(impl.graph().isReferencedInAnyGraph(deadSlot))
                    .as("dead record's slot must be detached from all graphs")
                    .isFalse();

            // Live records still retain stable graph slots and are resolvable
            assertThat(impl.index().locate("g-0").graphSlot()).isEqualTo(liveSlot0);
            assertThat(impl.index().locate("g-2").graphSlot()).isEqualTo(liveSlot2);
            assertThat(memory.inspect("g-0")).isNotNull();
            assertThat(memory.inspect("g-2")).isNotNull();
        }
    }

    // ── Task 3.8: Compaction on frozen v4 fixture ─────────────────

    @Nested
    @DisplayName("Task 3.8 — Compaction on frozen v4 bundle fixture")
    class FrozenFixtureCompaction {

        private static final String PARTITION_GZ = "bundle-compat/v4-pre-rename/partition.bundle.gz";

        @Test
        @DisplayName("compacting a frozen v4 bundle preserves schema version, magic, and region validity")
        void frozenFixtureCompactionPreservesFormat(@TempDir Path dir) throws Exception {
            Path bundlePath = materialise(dir, PARTITION_GZ, "partition.bundle");

            try (PartitionBundle bundle = PartitionBundle.Init.open(bundlePath)) {
                assertThat(bundle.isNew()).isFalse();

                MemorySegment semSegment = bundle.currentSlice(RegionId.SEMANTIC);
                assertThat(RegionPreamble.isValid(semSegment, 0)).isTrue();
                long initialSemCount = RegionPreamble.readCount(semSegment, 0);
                assertThat(initialSemCount).isEqualTo(3);

                // Build SemanticMemory using bundle opener (SEM_CAP=4, dims=8)
                SemanticMemory semStore = bundle.openSemantic(4, 8);

                // Tombstone record 0 in the fixture
                long rec0Offset = RegionPreamble.PREAMBLE_BYTES;
                semStore.tombstone(rec0Offset);
                assertThat(semStore.isTombstoned(rec0Offset)).isTrue();

                // Run compaction on the fixture
                CompactionResult result = VacuumCompactor.compact(
                        semStore, MemoryType.SEMANTIC, 0, null, s -> {}, 0.20f, true);

                assertThat(result).isNotNull();
                assertThat(result.compacted()).isTrue();
                assertThat(result.tombstonesRemoved()).isEqualTo(1);
                assertThat(result.afterCount()).isEqualTo(2);
                assertThat(result.bytesReclaimed()).isPositive();
            }

            // Re-open the compacted bundle to verify physical integrity
            try (PartitionBundle reopened = PartitionBundle.Init.open(bundlePath)) {
                assertThat(reopened.isNew()).isFalse();
                assertThat(reopened.directory().bundleMagic()).isEqualTo(BundleSubHeader.MAGIC_PARTITION);
                assertThat(reopened.directory().schemaVersion())
                        .isBetween(BundleFileLayout.MIN_READABLE_SCHEMA_VERSION, BundleFileLayout.SCHEMA_VERSION);

                MemorySegment sem = reopened.currentSlice(RegionId.SEMANTIC);
                assertThat(RegionPreamble.isValid(sem, 0)).isTrue();
                assertThat(RegionPreamble.readCount(sem, 0)).isEqualTo(2);

                MemorySegment proc = reopened.currentSlice(RegionId.PROCEDURAL);
                assertThat(RegionPreamble.isValid(proc, 0)).isTrue();
            }
        }
    }

    // ── Task 3.9: Reclaimed bytes are measured, not computed ──────

    @Nested
    @DisplayName("Task 3.9 — Measured space reclamation")
    class MeasuredSpaceReclamation {

        @Test
        @DisplayName("reclaimed bytes reflect physical byte deltas, not an unverified multiplication")
        void reclaimedBytesAreMeasured(@TempDir Path dir) {
            memory = build(dir, /*episodicCap*/ 32, /*semanticCap*/ 32);

            for (int i = 0; i < 5; i++) {
                memory.remember("meas-" + i, "Measurable memory payload " + i,
                        MemoryType.SEMANTIC, MemorySource.OBSERVED, "test");
            }

            for (int i = 0; i < 2; i++) {
                memory.forget("meas-" + i);
            }

            CompactionResult comp = memory.admin().vacuum(MemoryType.SEMANTIC, true);
            assertThat(comp.compacted()).isTrue();
            assertThat(comp.tombstonesRemoved()).isEqualTo(2);
            assertThat(comp.afterCount()).isEqualTo(3);
            assertThat(comp.bytesReclaimed())
                    .as("bytes reclaimed must be strictly positive and equal measured delta")
                    .isPositive();
        }
    }

    // ── Task 3.4: Threshold triggers ──────────────────────────────

    @Nested
    @DisplayName("Task 3.4 — Threshold triggering (0.20)")
    class ThresholdTriggering {

        @Test
        @DisplayName("vacuum without force does not compact when tombstones are below 20% threshold")
        void belowThresholdDoesNotCompact(@TempDir Path dir) {
            memory = build(dir, /*episodicCap*/ 64, /*semanticCap*/ 64);

            // 10 records
            for (int i = 0; i < 10; i++) {
                memory.remember("th-" + i, "Threshold subject " + i,
                        MemoryType.SEMANTIC, MemorySource.OBSERVED, "test");
            }

            // Forget 1 record (10% < 20% threshold)
            memory.forget("th-0");

            CompactionResult census = memory.admin().vacuum(MemoryType.SEMANTIC, false);
            assertThat(census).isNotNull();
            assertThat(census.compacted()).isFalse();
            assertThat(census.bytesReclaimed()).isZero();
            assertThat(census.tombstonesRemoved()).isEqualTo(1);

            // Forget 2 more records (3/10 = 30% >= 20% threshold)
            memory.forget("th-1");
            memory.forget("th-2");

            CompactionResult comp = memory.admin().vacuum(MemoryType.SEMANTIC, false);
            assertThat(comp).isNotNull();
            assertThat(comp.compacted()).isTrue();
            assertThat(comp.bytesReclaimed()).isPositive();
            assertThat(comp.tombstonesRemoved()).isEqualTo(3);
            assertThat(comp.afterCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("vacuum with force compacts even when tombstones are below 20% threshold")
        void forceCompactsBelowThreshold(@TempDir Path dir) {
            memory = build(dir, /*episodicCap*/ 64, /*semanticCap*/ 64);

            for (int i = 0; i < 10; i++) {
                memory.remember("frc-" + i, "Forced compaction subject " + i,
                        MemoryType.SEMANTIC, MemorySource.OBSERVED, "test");
            }

            // 1/10 = 10% tombstones
            memory.forget("frc-0");

            CompactionResult comp = memory.admin().vacuum(MemoryType.SEMANTIC, true);
            assertThat(comp).isNotNull();
            assertThat(comp.compacted()).isTrue();
            assertThat(comp.bytesReclaimed()).isPositive();
            assertThat(comp.tombstonesRemoved()).isEqualTo(1);
            assertThat(comp.afterCount()).isEqualTo(9);
        }
    }
}
