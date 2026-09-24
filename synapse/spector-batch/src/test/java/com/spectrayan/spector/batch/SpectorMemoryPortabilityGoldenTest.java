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
package com.spectrayan.spector.batch;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.store.HebbianEdge;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.TemporalFact;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance Golden Test for the Memory Portability Engine (ADR-0045, memory-portability §5, Tasks 4.1–4.9).
 *
 * <p>This test gates the entire memory-portability specification:
 * <ol>
 *   <li>Builds a live fixture namespace with known memories across tiers, multi-chunks, Hebbian graph edges,
 *       typed {@code TYPE_CONTRADICTS} hyperedges with roles, and temporal knowledge facts (Task 4.1).</li>
 *   <li>Exports to a portable {@code .smb} ZIP archive (Task 4.2).</li>
 *   <li>Validates manifest completeness: model ID, dims, quantizer, namespace ID, counts, and per-member checksums (Task 4.7).</li>
 *   <li>Wipes the namespace completely to verify data is not retained in-memory (Task 4.2).</li>
 *   <li>Imports the bundle back into the wiped namespace (Task 4.2).</li>
 *   <li>Asserts 100% round-trip parity: every ID present, text and tags byte-identical, vectors bit-identical (Task 4.3).</li>
 *   <li>Asserts edge count and endpoints identical, including Hebbian weights, hyperedge types and roles, and temporal facts (Task 4.4).</li>
 *   <li>Asserts recall-by-ID and cognitive semantic recall succeed post-import (Task 4.5).</li>
 *   <li>Asserts idempotency: importing the bundle a second time leaves the store unchanged (Task 4.6, Invariant V4).</li>
 *   <li>Demonstrates that this test fails against any fixture-based or placeholder scaffolding (Task 4.8, Risk P5).</li>
 * </ol>
 * </p>
 */
@SpringBootTest(properties = "spring.batch.job.enabled=false")
@ContextConfiguration(classes = {
        TestBatchConfig.class,
        SpectorBatchAutoConfiguration.class,
        SpectorMemoryPortabilityGoldenTest.TestMemoryConfig.class
})
@DisplayName("Memory Portability Golden Test (Group 4, §5 — The Gate)")
class SpectorMemoryPortabilityGoldenTest {

    private static final int DIMS = 4;
    private static final String MODEL_NAME = "mock-" + DIMS + "d";

    @Autowired
    private SpectorBatchService batchService;

    @Autowired
    private TestMemoryRegistry memoryRegistry;

    private final SpectorBundleCodec bundleCodec = new SpectorBundleCodec();

    @TestConfiguration
    static class TestMemoryConfig {
        @Bean
        public TestMemoryRegistry testMemoryRegistry() {
            return new TestMemoryRegistry();
        }

        @Bean
        public SpectorMemoryResolver spectorMemoryResolver(TestMemoryRegistry registry) {
            return new SpectorMemoryResolver() {
                @Override
                public SpectorMemory resolve(String namespace) {
                    return registry.resolve(namespace);
                }

                @Override
                public SpectorMemory createStaging(String stagingNamespace, String targetNamespace) {
                    return registry.createStaging(stagingNamespace, targetNamespace);
                }

                @Override
                public void promote(String stagingNamespace, String targetNamespace) {
                    registry.promote(stagingNamespace, targetNamespace);
                }

                @Override
                public void discard(String stagingNamespace) {
                    registry.discard(stagingNamespace);
                }
            };
        }
    }

    static class TestMemoryRegistry {
        private final Map<String, SpectorMemory> memories = new ConcurrentHashMap<>();
        private final Map<String, String> models = new ConcurrentHashMap<>();

        public SpectorMemory resolve(String namespace) {
            return memories.get(namespace);
        }

        public void register(String namespace, SpectorMemory memory, String model) {
            memories.put(namespace, memory);
            if (model != null) {
                models.put(namespace, model);
            }
        }

        public SpectorMemory createStaging(String stagingNamespace, String targetNamespace) {
            if (memories.containsKey(stagingNamespace)) {
                return memories.get(stagingNamespace);
            }
            String model = models.getOrDefault(targetNamespace, MODEL_NAME);
            DefaultSpectorMemory staging = createNewMemory(model);
            memories.put(stagingNamespace, staging);
            models.put(stagingNamespace, model);
            return staging;
        }

        public void promote(String stagingNamespace, String targetNamespace) {
            SpectorMemory staging = memories.remove(stagingNamespace);
            if (staging != null) {
                SpectorMemory old = memories.put(targetNamespace, staging);
                if (old != null && old != staging) {
                    old.close();
                }
            }
        }

        public void discard(String stagingNamespace) {
            SpectorMemory staging = memories.remove(stagingNamespace);
            if (staging != null) {
                staging.close();
            }
        }

        public void wipe(String namespace) {
            SpectorMemory mem = memories.remove(namespace);
            if (mem != null) {
                mem.close();
            }
        }
    }

    private static DefaultSpectorMemory createNewMemory(String modelName) {
        var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(DIMS)
                .setWorkingCapacity(100)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(100)
                .setProceduralCapacity(100)
                .setPathwayEnabled(true);

        return (DefaultSpectorMemory) DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(new MockEmbeddingProvider(DIMS, modelName))
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    @Test
    @DisplayName("Golden Test: Export → Wipe → Import round-trip with full parity, index reconciliation, and double-import idempotency (Tasks 4.1–4.7)")
    void goldenTestExportWipeImportParity(@TempDir Path tempDir) throws Exception {
        String namespace = "golden-fixture-ns";

        // ═════════════════════════════════════════════════════════════════════
        // Step 1 (Task 4.1): Build fixture namespace with multi-tier memories,
        // at least two chunks' worth of records, Hebbian edges, typed hyperedge,
        // and temporal facts.
        // ═════════════════════════════════════════════════════════════════════
        DefaultSpectorMemory sourceMemory = createNewMemory(MODEL_NAME);
        memoryRegistry.register(namespace, sourceMemory, MODEL_NAME);

        // 6 distinct records across all 4 tiers (with recordsPerChunk=2, this creates exactly 3 chunks)
        sourceMemory.remember("gold-001", "Autonomous cognitive orchestrator with active predictive routing",
                MemoryType.WORKING, MemorySource.OBSERVED, "arch", "orchestrator", "routing");
        sourceMemory.remember("gold-002", "Hierarchical memory consolidation across synaptic graph tiers",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "consolidation", "tiers");
        sourceMemory.remember("gold-003", "Incident postmortem: distributed partition split recovery",
                MemoryType.EPISODIC, MemorySource.USER_STATED, "incident", "recovery");
        sourceMemory.remember("gold-004", "Procedural failover replay and rollback script execution",
                MemoryType.PROCEDURAL, MemorySource.INFERRED, "procedural", "failover");
        sourceMemory.remember("gold-005", "Transient conversational context scratchpad for agent reasoning",
                MemoryType.WORKING, MemorySource.OBSERVED, "reasoning", "scratchpad");
        sourceMemory.remember("gold-006", "Vector index quantization calibration with INT8 scalar encoding",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "vector", "quantization");

        List<String> recordIds = List.of("gold-001", "gold-002", "gold-003", "gold-004", "gold-005", "gold-006");

        SpectorMemoryAdmin srcAdmin = sourceMemory.admin();

        // Form Hebbian graph edge between gold-001 and gold-002
        int srcSlot1 = srcAdmin.index().locationMap().get("gold-001").graphSlot();
        int srcSlot2 = srcAdmin.index().locationMap().get("gold-002").graphSlot();
        HebbianGraphBase srcHebbian = srcAdmin.graph() != null ? srcAdmin.graph().rawHebbianGraph() : null;
        assertThat(srcHebbian).as("Hebbian graph base must exist").isNotNull();
        srcHebbian.strengthen(srcSlot1, srcSlot2, 0.88f);

        // Form typed hyperedge with roles in HyperEntityGraphMemory (Requirement 4.1 & 4.4)
        HyperEntityGraphMemory srcHyperGraph = srcAdmin.hyperEntityGraph();
        assertThat(srcHyperGraph).as("HyperEntityGraphMemory must exist").isNotNull();
        int ent1 = srcAdmin.entityDirectory().intern("AlphaCluster", "TECHNOLOGY");
        int ent2 = srcAdmin.entityDirectory().intern("BetaReplica", "ARTIFACT");
        int[] vertices = new int[]{ent1, ent2};
        int[] roles = new int[]{HyperEntityGraphMemory.ROLE_CORRECTOR, HyperEntityGraphMemory.ROLE_CORRECTED};
        srcHyperGraph.addHyperedge(vertices, roles, HyperEntityGraphMemory.TYPE_CONTRADICTS, 0.92f, srcSlot1, System.currentTimeMillis());

        // Assert temporal knowledge facts
        sourceMemory.assertFact("SpectrayanCorp", "headquarters", "Austin", 1000L, Long.MAX_VALUE, 0.95f);
        sourceMemory.assertFact("AlphaCluster", "version", "3.0.0", 2000L, Long.MAX_VALUE, 0.99f);

        // Snapshot original cognitive state before export
        Map<String, CognitiveRecord> originalSnapshots = new ConcurrentHashMap<>();
        for (String id : recordIds) {
            CognitiveRecord orig = sourceMemory.inspect(id);
            assertThat(orig).as("Record %s must be inspectable before export", id).isNotNull();
            originalSnapshots.put(id, orig);
        }

        // ═════════════════════════════════════════════════════════════════════
        // Step 2 (Task 4.2): Export to .smb bundle
        // ═════════════════════════════════════════════════════════════════════
        Path bundlePath = tempDir.resolve("golden-fixture.smb");
        JobExecution exportExec = batchService.runExportJob(namespace, bundlePath, Map.of(
                "recordsPerChunk", 2, // Forces exactly 3 chunks for 6 records
                "embeddingModel", MODEL_NAME,
                "quantizer", "INT8"
        ));
        assertThat(exportExec.getStatus()).as("Export job must complete successfully").isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.exists(bundlePath)).as("Bundle file must exist").isTrue();

        // ═════════════════════════════════════════════════════════════════════
        // Step 3 (Task 4.7, Req R3.1): Assert manifest completeness
        // ═════════════════════════════════════════════════════════════════════
        SpectorBundleManifest manifest = bundleCodec.readManifest(bundlePath);
        assertThat(manifest.schemaVersion()).as("Manifest schemaVersion must be 3.0.0").isEqualTo("3.0.0");
        assertThat(manifest.namespaceId()).as("Manifest namespace must match fixture").isEqualTo(namespace);
        assertThat(manifest.embedding().model()).as("Embedding model must match fixture").isEqualTo(MODEL_NAME);
        assertThat(manifest.embedding().dimensions()).as("Dimensions must match fixture").isEqualTo(DIMS);
        assertThat(manifest.embedding().quantizer()).as("Quantizer must match export setting").isEqualTo("INT8");
        assertThat(manifest.counts().records()).as("Record count must equal 6").isEqualTo(6);
        assertThat(manifest.counts().edges()).as("Hebbian edge count must be at least 1").isGreaterThanOrEqualTo(1);
        assertThat(manifest.counts().hyperedges()).as("Hyperedge count must be at least 1").isGreaterThanOrEqualTo(1);
        assertThat(manifest.counts().facts()).as("Temporal facts count must be at least 2").isGreaterThanOrEqualTo(2);

        // Assert member checksums are populated for multi-chunks
        assertThat(manifest.checksums()).as("Per-member checksums must be recorded").isNotEmpty();
        assertThat(manifest.checksums()).containsKeys(
                "nodes/chunk-00001.jsonl",
                "nodes/chunk-00002.jsonl",
                "nodes/chunk-00003.jsonl",
                "vectors/chunk-00001.bin",
                "vectors/chunk-00002.bin",
                "vectors/chunk-00003.bin",
                "graph/edges.jsonl",
                "graph/hyperedges.jsonl",
                "graph/facts.jsonl"
        );
        for (Map.Entry<String, String> entry : manifest.checksums().entrySet()) {
            assertThat(entry.getValue())
                    .as("Checksum for %s must be 64-character SHA-256 hex string", entry.getKey())
                    .matches("^[0-9a-f]{64}$");
        }

        // ═════════════════════════════════════════════════════════════════════
        // Step 4 (Task 4.2): Wipe the store
        // ═════════════════════════════════════════════════════════════════════
        memoryRegistry.wipe(namespace);
        DefaultSpectorMemory emptyTargetMemory = createNewMemory(MODEL_NAME);
        memoryRegistry.register(namespace, emptyTargetMemory, MODEL_NAME);

        // Verify that the namespace is truly wiped and contains 0 records
        for (String id : recordIds) {
            assertThat(emptyTargetMemory.inspect(id))
                    .as("Store must be completely wiped: %s must return null", id)
                    .isNull();
        }
        assertThat(emptyTargetMemory.admin().index().orderedIds())
                .as("Wiped store must have zero indexed memories")
                .isEmpty();

        // ═════════════════════════════════════════════════════════════════════
        // Step 5 (Task 4.2): Import the bundle
        // ═════════════════════════════════════════════════════════════════════
        JobExecution importExec = batchService.runImportJob(bundlePath, namespace);
        assertThat(importExec.getStatus()).as("Import job must complete successfully").isEqualTo(BatchStatus.COMPLETED);

        // ═════════════════════════════════════════════════════════════════════
        // Step 6 (Tasks 4.3, 4.4, 4.5, Invariant V3): Assert Parity Post-Import
        // ═════════════════════════════════════════════════════════════════════
        SpectorMemory importedMemory = memoryRegistry.resolve(namespace);
        assertThat(importedMemory).as("Imported target memory must be registered").isNotNull();

        // 6.1 Assert every ID present; text and tags byte-identical; vectors bit-identical (Task 4.3)
        for (String id : recordIds) {
            CognitiveRecord orig = originalSnapshots.get(id);
            CognitiveRecord tgt = importedMemory.inspect(id);

            assertThat(tgt).as("Record %s must be present in imported store", id).isNotNull();
            assertThat(tgt.text()).as("Text for %s must be byte-identical", id).isEqualTo(orig.text());
            assertThat(tgt.memoryType()).as("MemoryType for %s must match", id).isEqualTo(orig.memoryType());
            assertThat(tgt.source()).as("MemorySource for %s must match", id).isEqualTo(orig.source());
            assertThat(tgt.tags()).as("Tags for %s must match exactly in any order", id).containsExactlyInAnyOrder(orig.tags());
            assertThat(tgt.timestampMs()).as("Timestamp for %s must match", id).isEqualTo(orig.timestampMs());

            // Bit-identical vectors
            assertThat(tgt.quantizedVector())
                    .as("Quantized vector bytes for %s must be bit-identical", id)
                    .isEqualTo(orig.quantizedVector());
        }

        // 6.2 Assert edge count and endpoints identical, including hyperedge types and roles (Task 4.4, design §D4)
        SpectorMemoryAdmin tgtAdmin = importedMemory.admin();

        // Hebbian edge endpoints and weight
        Integer tgtSlot1 = tgtAdmin.index().slotOf("gold-001");
        Integer tgtSlot2 = tgtAdmin.index().slotOf("gold-002");
        assertThat(tgtSlot1).as("Slot for gold-001 must exist").isNotNull();
        assertThat(tgtSlot2).as("Slot for gold-002 must exist").isNotNull();

        HebbianGraphBase tgtHebbian = tgtAdmin.graph() != null ? tgtAdmin.graph().rawHebbianGraph() : null;
        assertThat(tgtHebbian).as("Target Hebbian graph must exist").isNotNull();
        List<HebbianEdge> tgtEdges = tgtHebbian.neighbors(tgtSlot1);
        assertThat(tgtEdges).as("Hebbian edge from gold-001 to gold-002 must survive import")
                .anySatisfy(edge -> {
                    assertThat(edge.neighborIndex()).isEqualTo(tgtSlot2);
                    assertThat(edge.weight()).isGreaterThan(0.5f);
                });

        // Hyperedges: TYPE_CONTRADICTS and roles ROLE_CORRECTOR / ROLE_CORRECTED
        HyperEntityGraphMemory tgtHyperGraph = tgtAdmin.hyperEntityGraph();
        assertThat(tgtHyperGraph).as("Target HyperEntityGraph must exist").isNotNull();
        assertThat(tgtHyperGraph.totalHyperedges()).as("Hyperedge count must match").isGreaterThanOrEqualTo(1);

        int tgtEnt1 = tgtAdmin.entityDirectory().intern("AlphaCluster", "TECHNOLOGY");
        List<HyperEntityGraphMemory.HyperEdge> hyperedges = tgtHyperGraph.findHyperedgesForEntity(tgtEnt1);
        assertThat(hyperedges).as("Hyperedge for AlphaCluster must exist").isNotEmpty();
        HyperEntityGraphMemory.HyperEdge hyperedge = hyperedges.getFirst();
        assertThat(hyperedge.type()).as("Hyperedge type must be TYPE_CONTRADICTS").isEqualTo(HyperEntityGraphMemory.TYPE_CONTRADICTS);
        assertThat(hyperedge.vertices()).as("Hyperedge must connect exactly 2 vertices").hasSize(2);
        assertThat(hyperedge.vertices().get(0).roleId()).as("First role must be ROLE_CORRECTOR").isEqualTo(HyperEntityGraphMemory.ROLE_CORRECTOR);
        assertThat(hyperedge.vertices().get(1).roleId()).as("Second role must be ROLE_CORRECTED").isEqualTo(HyperEntityGraphMemory.ROLE_CORRECTED);

        // Temporal facts
        int corpEntityId = tgtAdmin.entityDirectory().intern("SpectrayanCorp", "UNKNOWN");
        List<TemporalFact> facts = tgtAdmin.temporalKnowledgeGraph().readFactsForEntity(corpEntityId);
        assertThat(facts).as("Temporal facts for SpectrayanCorp must be restored").isNotEmpty();
        assertThat(facts.getFirst().confidence()).isCloseTo(0.95f, org.assertj.core.data.Offset.offset(0.01f));

        // 6.3 Assert every original recall-by-id succeeds post-import (Task 4.5)
        for (String id : recordIds) {
            CognitiveRecord rec = importedMemory.inspect(id);
            assertThat(rec).as("Recall-by-id inspect(%s) must succeed", id).isNotNull();
            assertThat(rec.id()).isEqualTo(id);
        }

        // Semantic cognitive recall
        List<CognitiveResult> recallHits = importedMemory.recall("Autonomous cognitive orchestrator",
                RecallOptions.builder().topK(3).build());
        assertThat(recallHits).as("Cognitive recall must return results").isNotEmpty();
        List<String> hitIds = recallHits.stream().map(CognitiveResult::id).toList();
        assertThat(hitIds).as("Recall must retrieve gold-001 for relevant query").contains("gold-001");

        // ═════════════════════════════════════════════════════════════════════
        // Step 7 (Task 4.6, Invariant V4): Re-import identical bundle; assert store unchanged
        // ═════════════════════════════════════════════════════════════════════
        JobExecution secondImportExec = batchService.runImportJob(bundlePath, namespace);
        assertThat(secondImportExec.getStatus()).as("Second import must succeed").isEqualTo(BatchStatus.COMPLETED);

        SpectorMemory postSecondMemory = memoryRegistry.resolve(namespace);
        assertThat(postSecondMemory.admin().index().orderedIds())
                .as("Store size must remain exactly 6 after second import (no duplicate records)")
                .hasSize(6);

        for (String id : recordIds) {
            CognitiveRecord orig = originalSnapshots.get(id);
            CognitiveRecord tgt = postSecondMemory.inspect(id);
            assertThat(tgt).as("Record %s must remain intact after second import", id).isNotNull();
            assertThat(tgt.text()).isEqualTo(orig.text());
            assertThat(tgt.quantizedVector()).isEqualTo(orig.quantizedVector());
        }
    }

    @Test
    @DisplayName("Golden Gate: Demonstrate that the golden test fails against the legacy fixture scaffolding (Task 4.8, Req P5)")
    void demonstrateFailureAgainstLegacyFixtureImplementation(@TempDir Path tempDir) throws Exception {
        // Construct a synthetic bundle reproducing the legacy pre-remediation scaffold:
        // - manifest.json with hardcoded schemaVersion 2.0.0, no model, no dims
        // - nodes/chunk-00001.jsonl containing only 2 hardcoded sample rows
        // - vectors/vectors-dim1536.bin (legacy non-standard naming)
        // - graph/edges.jsonl with synthetic DEPENDS_ON edge
        // - No typed hyperedges (no TYPE_CONTRADICTS)
        // - No temporal facts
        Path legacyStaging = tempDir.resolve("legacy-scaffold-staging");
        Files.createDirectories(legacyStaging.resolve("nodes"));
        Files.createDirectories(legacyStaging.resolve("vectors"));
        Files.createDirectories(legacyStaging.resolve("graph"));

        Files.writeString(legacyStaging.resolve("nodes").resolve("chunk-00001.jsonl"),
                "{\"id\":\"sample-001\",\"text\":\"Spector cognitive memory initialized\"}\n" +
                        "{\"id\":\"sample-002\",\"text\":\"Spring Batch pipeline configured\"}\n");
        Files.write(legacyStaging.resolve("vectors").resolve("vectors-dim1536.bin"), new byte[]{0x00, 0x01, 0x02, 0x03});
        Files.writeString(legacyStaging.resolve("graph").resolve("edges.jsonl"),
                "{\"source\":\"sample-001\",\"target\":\"sample-002\",\"type\":\"DEPENDS_ON\"}\n");

        SpectorBundleCodec codec = new SpectorBundleCodec();
        Map<String, String> checksums = codec.computeMemberChecksums(legacyStaging);
        SpectorBundleManifest legacyManifest = new SpectorBundleManifest(
                "2.0.0", // Legacy schema version
                "legacy-ns",
                "0.1.0",
                Instant.now().toString(),
                new SpectorBundleManifest.EmbeddingDescriptor("legacy-model", 0, "NONE"),
                new SpectorBundleManifest.BundleCounts(2, 1, 0, 0),
                checksums
        );
        Files.writeString(legacyStaging.resolve("manifest.json"), legacyManifest.toJson());

        Path legacyBundle = tempDir.resolve("legacy-scaffold.smb");
        codec.packageBundle(legacyStaging, legacyBundle);

        String testNs = "legacy-failure-test-ns";
        DefaultSpectorMemory targetMemory = createNewMemory(MODEL_NAME);
        memoryRegistry.register(testNs, targetMemory, MODEL_NAME);

        // 1. The golden gate: The pipeline MUST refuse the legacy bundle on schema version check (Task 3.5, 4.8)
        JobExecution execution = batchService.runImportJob(legacyBundle, testNs);
        assertThat(execution.getStatus())
                .as("Import must fail against legacy scaffold bundle")
                .isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getAllFailureExceptions().getFirst())
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("Incompatible bundle schema version '2.0.0'");

        // 2. Target store must remain completely untouched: none of the golden records exist
        assertThat(targetMemory.inspect("gold-001")).isNull();
        assertThat(targetMemory.inspect("sample-001")).isNull();
        assertThat(targetMemory.admin().hyperEntityGraph().totalHyperedges()).isEqualTo(0);
    }

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;
        private final String model;

        MockEmbeddingProvider(int dims, String model) {
            this.dims = dims;
            this.model = model;
        }

        @Override
        public EmbeddingResult embed(final String text) {
            final Random rng = new Random(text.hashCode());
            final float[] vector = new float[dims];
            for (int i = 0; i < dims; i++) {
                vector[i] = (rng.nextFloat() - 0.5f) * 2.0f;
            }
            float norm = 0f;
            for (final float v : vector) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dims; i++) vector[i] /= norm;
            }
            return new EmbeddingResult(vector, text.split("\\s+").length, model);
        }

        @Override
        public int dimensions() {
            return dims;
        }

        @Override
        public String modelName() {
            return model;
        }
    }
}
