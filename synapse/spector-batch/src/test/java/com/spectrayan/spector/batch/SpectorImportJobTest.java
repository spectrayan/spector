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
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@ContextConfiguration(classes = {
        TestBatchConfig.class,
        SpectorBatchAutoConfiguration.class,
        SpectorImportJobTest.TestMemoryConfig.class
})
@DisplayName("SpectorImportJob Integration Tests (Group 3, Tasks 3.1-3.9)")
class SpectorImportJobTest {

    private static final int DIMS = 4;

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

        public SpectorMemory resolve(String namespace) {
            return memories.get(namespace);
        }

        public void register(String namespace, SpectorMemory memory) {
            memories.put(namespace, memory);
        }

        public SpectorMemory createStaging(String stagingNamespace, String targetNamespace) {
            // Create fresh staging memory with same parameters as target
            DefaultSpectorMemory staging = createNewMemory("mock-" + DIMS + "d");
            memories.put(stagingNamespace, staging);
            return staging;
        }

        public void promote(String stagingNamespace, String targetNamespace) {
            SpectorMemory staging = memories.remove(stagingNamespace);
            if (staging != null) {
                SpectorMemory old = memories.put(targetNamespace, staging);
                if (old != null) {
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
    @DisplayName("Import parses multi-chunk bundle and populates target memory with full parity (Tasks 3.1-3.4)")
    void importReadsBundleAndPopulatesMemoryWithParity(@TempDir Path tempDir) throws Exception {
        String sourceNs = "source-parity-ns";
        String targetNs = "target-parity-ns";

        DefaultSpectorMemory sourceMemory = createNewMemory("mock-" + DIMS + "d");
        memoryRegistry.register(sourceNs, sourceMemory);

        // 1. Ingest 5 records into source memory
        sourceMemory.remember("mem-001", "Architecture consensus protocol", MemoryType.WORKING, MemorySource.OBSERVED, "arch", "consensus");
        sourceMemory.remember("mem-002", "Distributed consensus state machine", MemoryType.SEMANTIC, MemorySource.OBSERVED, "consensus", "state");
        sourceMemory.remember("mem-003", "Session interaction replay log", MemoryType.EPISODIC, MemorySource.USER_STATED, "session", "replay");
        sourceMemory.remember("mem-004", "Procedural failover recovery sequence", MemoryType.PROCEDURAL, MemorySource.INFERRED, "failover");
        sourceMemory.remember("mem-005", "Working memory scratchpad context", MemoryType.WORKING, MemorySource.OBSERVED, "scratchpad");

        SpectorMemoryAdmin srcAdmin = sourceMemory.admin();

        // 2. Add Hebbian edge
        int slot1 = srcAdmin.index().locationMap().get("mem-001").graphSlot();
        int slot2 = srcAdmin.index().locationMap().get("mem-002").graphSlot();
        HebbianGraphBase srcHebbian = srcAdmin.graph() != null ? srcAdmin.graph().rawHebbianGraph() : null;
        if (srcHebbian != null) {
            srcHebbian.strengthen(slot1, slot2, 0.85f);
        }

        // 3. Add typed hyperedge with roles
        HyperEntityGraphMemory srcHyperGraph = srcAdmin.hyperEntityGraph();
        if (srcHyperGraph != null) {
            int ent1 = srcAdmin.entityDirectory().intern("AlphaEntity", "TECHNOLOGY");
            int ent2 = srcAdmin.entityDirectory().intern("BetaEntity", "ARTIFACT");
            int[] vertices = new int[]{ent1, ent2};
            int[] roles = new int[]{HyperEntityGraphMemory.ROLE_CORRECTOR, HyperEntityGraphMemory.ROLE_CORRECTED};
            srcHyperGraph.addHyperedge(vertices, roles, HyperEntityGraphMemory.TYPE_CONTRADICTS, 0.9f, slot1, System.currentTimeMillis());
        }

        // 4. Assert temporal fact
        sourceMemory.assertFact("SpectrayanCorp", "headquarters", "Austin", 1000L, Long.MAX_VALUE, 0.95f);

        // 5. Export to SMB bundle with chunk size 2 (yielding 3 chunks for 5 records)
        Path bundlePath = tempDir.resolve("parity-bundle.smb");
        JobExecution exportExec = batchService.runExportJob(sourceNs, bundlePath, Map.of(
                "recordsPerChunk", 2,
                "embeddingModel", "mock-" + DIMS + "d",
                "quantizer", "NONE"
        ));
        assertThat(exportExec.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 6. Setup fresh target memory
        DefaultSpectorMemory targetMemory = createNewMemory("mock-" + DIMS + "d");
        memoryRegistry.register(targetNs, targetMemory);

        // 7. Run import job
        JobExecution importExec = batchService.runImportJob(bundlePath, targetNs);
        assertThat(importExec.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 8. Assert parity in target memory
        SpectorMemory resolvedTarget = memoryRegistry.resolve(targetNs);
        assertThat(resolvedTarget).isNotNull();

        for (String id : List.of("mem-001", "mem-002", "mem-003", "mem-004", "mem-005")) {
            CognitiveRecord srcRec = sourceMemory.inspect(id);
            CognitiveRecord tgtRec = resolvedTarget.inspect(id);

            assertThat(tgtRec).as("Record '%s' must exist in target", id).isNotNull();
            assertThat(tgtRec.text()).isEqualTo(srcRec.text());
            assertThat(tgtRec.memoryType()).isEqualTo(srcRec.memoryType());
            assertThat(tgtRec.tags()).containsExactlyInAnyOrder(srcRec.tags());
            assertThat(tgtRec.timestampMs()).isEqualTo(srcRec.timestampMs());
        }

        // 9. Assert Hebbian graph edge reconstructed with correct endpoints
        SpectorMemoryAdmin tgtAdmin = resolvedTarget.admin();
        Integer tgtSlot1 = tgtAdmin.index().slotOf("mem-001");
        Integer tgtSlot2 = tgtAdmin.index().slotOf("mem-002");
        assertThat(tgtSlot1).isNotNull();
        assertThat(tgtSlot2).isNotNull();

        HebbianGraphBase tgtHebbian = tgtAdmin.graph() != null ? tgtAdmin.graph().rawHebbianGraph() : null;
        assertThat(tgtHebbian).isNotNull();
        List<HebbianEdge> neighbors = tgtHebbian.neighbors(tgtSlot1);
        assertThat(neighbors).anySatisfy(e -> {
            assertThat(e.neighborIndex()).isEqualTo(tgtSlot2);
            assertThat(e.weight()).isGreaterThan(0.5f);
        });

        // 10. Assert typed hyperedge reconstructed with type and roles
        HyperEntityGraphMemory tgtHyperGraph = tgtAdmin.hyperEntityGraph();
        assertThat(tgtHyperGraph).isNotNull();
        assertThat(tgtHyperGraph.totalHyperedges()).isGreaterThanOrEqualTo(1);

        int ent1 = tgtAdmin.entityDirectory().intern("AlphaEntity", "TECHNOLOGY");
        List<HyperEntityGraphMemory.HyperEdge> hedges = tgtHyperGraph.findHyperedgesForEntity(ent1);
        assertThat(hedges).isNotEmpty();
        HyperEntityGraphMemory.HyperEdge hedge = hedges.getFirst();
        assertThat(hedge.type()).isEqualTo(HyperEntityGraphMemory.TYPE_CONTRADICTS);
        assertThat(hedge.vertices()).hasSize(2);
        assertThat(hedge.vertices().get(0).roleId()).isEqualTo(HyperEntityGraphMemory.ROLE_CORRECTOR);
        assertThat(hedge.vertices().get(1).roleId()).isEqualTo(HyperEntityGraphMemory.ROLE_CORRECTED);

        // 11. Assert temporal facts reconstructed
        List<TemporalFact> facts = tgtAdmin.temporalKnowledgeGraph().readFactsForEntity(
                tgtAdmin.entityDirectory().intern("SpectrayanCorp", "UNKNOWN")
        );
        assertThat(facts).isNotEmpty();
        assertThat(facts.getFirst().confidence()).isCloseTo(0.95f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    @DisplayName("Import is idempotent by memory ID — re-import does not duplicate records (Task 3.6)")
    void importIsIdempotent(@TempDir Path tempDir) throws Exception {
        String sourceNs = "source-idempotent-ns";
        String targetNs = "target-idempotent-ns";

        DefaultSpectorMemory sourceMemory = createNewMemory("mock-" + DIMS + "d");
        memoryRegistry.register(sourceNs, sourceMemory);
        sourceMemory.remember("idemp-1", "First record", MemoryType.SEMANTIC, MemorySource.OBSERVED);
        sourceMemory.remember("idemp-2", "Second record", MemoryType.SEMANTIC, MemorySource.OBSERVED);

        Path bundlePath = tempDir.resolve("idempotent.smb");
        batchService.runExportJob(sourceNs, bundlePath, Map.of(
                "recordsPerChunk", 10,
                "embeddingModel", "mock-" + DIMS + "d"
        ));

        DefaultSpectorMemory targetMemory = createNewMemory("mock-" + DIMS + "d");
        memoryRegistry.register(targetNs, targetMemory);

        // First import
        JobExecution firstImport = batchService.runImportJob(bundlePath, targetNs);
        assertThat(firstImport.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        SpectorMemory resolvedAfterFirst = memoryRegistry.resolve(targetNs);
        assertThat(resolvedAfterFirst.inspect("idemp-1")).isNotNull();
        assertThat(resolvedAfterFirst.inspect("idemp-2")).isNotNull();
        assertThat(resolvedAfterFirst.admin().index().orderedIds()).hasSize(2);

        // Second import of the identical bundle
        JobExecution secondImport = batchService.runImportJob(bundlePath, targetNs);
        assertThat(secondImport.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        SpectorMemory resolvedAfterSecond = memoryRegistry.resolve(targetNs);
        assertThat(resolvedAfterSecond.admin().index().orderedIds())
                .as("Re-importing the same bundle must not duplicate records")
                .hasSize(2);
    }

    @Test
    @DisplayName("Import refuses incompatible bundle schema version (Task 3.5)")
    void importRefusesIncompatibleSchemaVersion(@TempDir Path tempDir) throws Exception {
        Path staging = tempDir.resolve("incompat-staging");
        Files.createDirectories(staging.resolve("nodes"));
        Files.createDirectories(staging.resolve("vectors"));
        Files.writeString(staging.resolve("nodes").resolve("chunk-00001.jsonl"), "{\"id\":\"x\",\"text\":\"hello\"}\n");
        Files.write(staging.resolve("vectors").resolve("chunk-00001.bin"), new byte[0]);

        SpectorBundleCodec codec = new SpectorBundleCodec();
        Map<String, String> checksums = codec.computeMemberChecksums(staging);
        SpectorBundleManifest manifest = new SpectorBundleManifest(
                "1.0.0", // Incompatible schema version
                "incompat-ns",
                "0.1.0",
                Instant.now().toString(),
                new SpectorBundleManifest.EmbeddingDescriptor("test-model", 0, "NONE"),
                new SpectorBundleManifest.BundleCounts(1, 0, 0, 0),
                checksums
        );
        Files.writeString(staging.resolve("manifest.json"), manifest.toJson());

        Path bundlePath = tempDir.resolve("incompatible-schema.smb");
        codec.packageBundle(staging, bundlePath);

        DefaultSpectorMemory targetMemory = createNewMemory("mock-" + DIMS + "d");
        memoryRegistry.register("incompat-target", targetMemory);

        JobExecution execution = batchService.runImportJob(bundlePath, "incompat-target");
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getAllFailureExceptions().getFirst())
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("Incompatible bundle schema version");

        // Target memory must be completely untouched
        assertThat(targetMemory.inspect("x")).isNull();
    }

    @Test
    @DisplayName("Import refuses model mismatch unless --reembed is given (Task 3.8)")
    void importRefusesModelMismatchWithoutReembed(@TempDir Path tempDir) throws Exception {
        String sourceNs = "model-mismatch-src";
        String targetNs = "model-mismatch-tgt";

        DefaultSpectorMemory sourceMemory = createNewMemory("mock-" + DIMS + "d");
        memoryRegistry.register(sourceNs, sourceMemory);
        sourceMemory.remember("model-node-1", "Model mismatch content", MemoryType.SEMANTIC, MemorySource.OBSERVED);

        Path bundlePath = tempDir.resolve("mismatch.smb");
        batchService.runExportJob(sourceNs, bundlePath, Map.of(
                "recordsPerChunk", 5,
                "embeddingModel", "model-Alpha"
        ));

        DefaultSpectorMemory targetMemory = createNewMemory("model-Beta");
        memoryRegistry.register(targetNs, targetMemory);

        // 1. Attempt import with mismatched model and reembed=false -> must refuse
        JobExecution failedExec = batchService.runImportJob(bundlePath, targetNs, Map.of(
                "targetModel", "model-Beta",
                "reembed", "false"
        ));
        assertThat(failedExec.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(failedExec.getAllFailureExceptions().getFirst())
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("does not match target store model");

        // Target store must be untouched
        assertThat(targetMemory.inspect("model-node-1")).isNull();

        // 2. Attempt import with mismatched model and reembed=true -> must succeed
        JobExecution successExec = batchService.runImportJob(bundlePath, targetNs, Map.of(
                "targetModel", "model-Beta",
                "reembed", "true"
        ));
        assertThat(successExec.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        SpectorMemory resolvedTarget = memoryRegistry.resolve(targetNs);
        assertThat(resolvedTarget.inspect("model-node-1")).isNotNull();
    }

    @Test
    @DisplayName("Failed import leaves target store byte-unchanged; staging namespace discarded (Task 3.7, 3.9)")
    void failedImportLeavesTargetStoreUntouched(@TempDir Path tempDir) throws Exception {
        String targetNs = "resilient-target-ns";

        // Setup target memory with 2 pre-existing records
        DefaultSpectorMemory targetMemory = createNewMemory("mock-" + DIMS + "d");
        memoryRegistry.register(targetNs, targetMemory);
        targetMemory.remember("pre-existing-1", "Initial memory 1", MemoryType.SEMANTIC, MemorySource.OBSERVED);
        targetMemory.remember("pre-existing-2", "Initial memory 2", MemoryType.SEMANTIC, MemorySource.OBSERVED);

        // Prepare an invalid/corrupt bundle with mismatched checksum
        Path staging = tempDir.resolve("corrupt-staging");
        Files.createDirectories(staging.resolve("nodes"));
        Files.createDirectories(staging.resolve("vectors"));
        Files.writeString(staging.resolve("nodes").resolve("chunk-00001.jsonl"), "{\"id\":\"corrupt-1\",\"text\":\"corrupt text\"}\n");
        Files.write(staging.resolve("vectors").resolve("chunk-00001.bin"), new byte[0]);

        SpectorBundleCodec codec = new SpectorBundleCodec();
        Map<String, String> checksums = Map.of("nodes/chunk-00001.jsonl", "sha256:0000000000000000000000000000000000000000000000000000000000000000"); // Bad checksum
        SpectorBundleManifest manifest = new SpectorBundleManifest(
                "3.0.0",
                targetNs,
                "0.1.0",
                Instant.now().toString(),
                new SpectorBundleManifest.EmbeddingDescriptor("mock-" + DIMS + "d", 0, "NONE"),
                new SpectorBundleManifest.BundleCounts(1, 0, 0, 0),
                checksums
        );
        Files.writeString(staging.resolve("manifest.json"), manifest.toJson());

        Path bundlePath = tempDir.resolve("corrupt-checksum.smb");
        codec.packageBundle(staging, bundlePath);

        // Run import job — must fail checksum verification
        JobExecution execution = batchService.runImportJob(bundlePath, targetNs);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);

        // Target memory must still have exactly the 2 pre-existing records and zero corrupted records
        SpectorMemory resolved = memoryRegistry.resolve(targetNs);
        assertThat(resolved.inspect("pre-existing-1")).isNotNull();
        assertThat(resolved.inspect("pre-existing-2")).isNotNull();
        assertThat(resolved.inspect("corrupt-1")).isNull();
        assertThat(resolved.admin().index().orderedIds()).hasSize(2);
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
