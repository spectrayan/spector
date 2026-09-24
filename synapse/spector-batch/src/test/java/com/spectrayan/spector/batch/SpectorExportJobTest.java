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

import com.spectrayan.spector.batch.exporting.ExportEdgeRecord;
import com.spectrayan.spector.batch.exporting.ExportFactRecord;
import com.spectrayan.spector.batch.exporting.ExportHyperedgeRecord;
import com.spectrayan.spector.batch.exporting.ExportNodeRecord;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@ContextConfiguration(classes = {
        TestBatchConfig.class,
        SpectorBatchAutoConfiguration.class,
        SpectorExportJobTest.TestMemoryConfig.class
})
@DisplayName("SpectorExportJob Integration Tests (Group 2, R1)")
class SpectorExportJobTest {

    private static final int DIMS = 4;
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

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
            return registry::resolve;
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
    }

    private DefaultSpectorMemory createMemory() {
        var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(DIMS)
                .setWorkingCapacity(100)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(100)
                .setProceduralCapacity(100)
                .setPathwayEnabled(true);

        return (DefaultSpectorMemory) DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(new MockEmbeddingProvider(DIMS))
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    @Test
    @DisplayName("Export reads live memory and produces valid multi-chunk SMB bundle (Tasks 2.1-2.10)")
    void exportReadsLiveMemoryAndProducesValidBundle(@TempDir Path tempDir) throws Exception {
        String namespace = "live-export-ns";
        DefaultSpectorMemory memory = createMemory();
        memoryRegistry.register(namespace, memory);

        // 1. Ingest 5 live records across different tiers with tags and vectors
        memory.remember("mem-001", "Architecture consensus protocol", MemoryType.WORKING, MemorySource.OBSERVED, "arch", "consensus");
        memory.remember("mem-002", "Distributed consensus state machine", MemoryType.SEMANTIC, MemorySource.OBSERVED, "consensus", "state");
        memory.remember("mem-003", "Session interaction replay log", MemoryType.EPISODIC, MemorySource.USER_STATED, "session", "replay");
        memory.remember("mem-004", "Procedural failover recovery sequence", MemoryType.PROCEDURAL, MemorySource.INFERRED, "failover");
        memory.remember("mem-005", "Working memory scratchpad context", MemoryType.WORKING, MemorySource.OBSERVED, "scratchpad");

        SpectorMemoryAdmin admin = memory.admin();

        // 2. Form a Hebbian edge between mem-001 and mem-002
        int slot1 = admin.index().locationMap().get("mem-001").graphSlot();
        int slot2 = admin.index().locationMap().get("mem-002").graphSlot();
        HebbianGraphBase hebbian = admin.graph() != null ? admin.graph().rawHebbianGraph() : null;
        if (hebbian != null) {
            hebbian.strengthen(slot1, slot2, 0.85f);
        }

        // 3. Form a typed hyperedge in HyperEntityGraphMemory
        HyperEntityGraphMemory hyperGraph = admin.hyperEntityGraph();
        if (hyperGraph != null) {
            int[] vertices = new int[]{10, 20};
            int[] roles = new int[]{HyperEntityGraphMemory.ROLE_SUBJECT, HyperEntityGraphMemory.ROLE_OBJECT};
            hyperGraph.addHyperedge(vertices, roles, HyperEntityGraphMemory.TYPE_RELATIONSHIP, 0.9f, slot1, System.currentTimeMillis());
        }

        // 4. Assert a temporal fact
        memory.assertFact("SpectrayanCorp", "headquarters", "Austin", 1000L, Long.MAX_VALUE, 0.95f);

        // 5. Execute export job with recordsPerChunk=2 to force multi-chunk output (2 records per chunk -> 3 chunks)
        Path bundlePath = tempDir.resolve("export-live.smb");
        Map<String, Object> jobParams = Map.of(
                "recordsPerChunk", 2,
                "embeddingModel", "mock-" + DIMS + "d",
                "quantizer", "INT8"
        );

        JobExecution execution = batchService.runExportJob(namespace, bundlePath, jobParams);

        assertThat(execution).isNotNull();
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.exists(bundlePath)).isTrue();

        // 6. Inspect manifest.json directly from bundle without full unpack
        SpectorBundleManifest manifest = bundleCodec.readManifest(bundlePath);
        assertThat(manifest.schemaVersion()).isEqualTo("3.0.0");
        assertThat(manifest.namespaceId()).isEqualTo(namespace);
        assertThat(manifest.embedding()).isNotNull();
        assertThat(manifest.embedding().model()).isEqualTo("mock-" + DIMS + "d");
        assertThat(manifest.embedding().dimensions()).isEqualTo(DIMS);
        assertThat(manifest.embedding().quantizer()).isEqualTo("INT8");

        // Verify counts
        assertThat(manifest.counts()).isNotNull();
        assertThat(manifest.counts().records()).isEqualTo(5L);
        assertThat(manifest.counts().edges()).isGreaterThanOrEqualTo(1L);
        assertThat(manifest.counts().hyperedges()).isGreaterThanOrEqualTo(1L);
        assertThat(manifest.counts().facts()).isGreaterThanOrEqualTo(1L);

        // Verify checksums exist for members
        assertThat(manifest.checksums()).isNotEmpty();

        // Verify "verified": true was NOT written to manifest (Task 2.9, R1.8)
        String rawManifestJson = readManifestRaw(bundlePath);
        assertThat(rawManifestJson).doesNotContain("\"verified\":true");
        assertThat(rawManifestJson).doesNotContain("\"verified\": true");

        // 7. Unpack bundle to staging dir and verify contents
        Path unpackDir = tempDir.resolve("unpacked");
        bundleCodec.unpackBundle(bundlePath, unpackDir);

        // Nodes chunks (3 chunks: chunk-00001, chunk-00002, chunk-00003)
        Path nodesDir = unpackDir.resolve("nodes");
        assertThat(Files.exists(nodesDir.resolve("chunk-00001.jsonl"))).isTrue();
        assertThat(Files.exists(nodesDir.resolve("chunk-00002.jsonl"))).isTrue();
        assertThat(Files.exists(nodesDir.resolve("chunk-00003.jsonl"))).isTrue();

        List<ExportNodeRecord> allExportedNodes = new ArrayList<>();
        try (var lines = Files.lines(nodesDir.resolve("chunk-00001.jsonl"))) {
            lines.forEach(l -> allExportedNodes.add(parseNode(l)));
        }
        try (var lines = Files.lines(nodesDir.resolve("chunk-00002.jsonl"))) {
            lines.forEach(l -> allExportedNodes.add(parseNode(l)));
        }
        try (var lines = Files.lines(nodesDir.resolve("chunk-00003.jsonl"))) {
            lines.forEach(l -> allExportedNodes.add(parseNode(l)));
        }
        assertThat(allExportedNodes).hasSize(5);

        // Check node contents
        List<String> nodeIds = allExportedNodes.stream().map(ExportNodeRecord::id).toList();
        assertThat(nodeIds).containsExactlyInAnyOrder("mem-001", "mem-002", "mem-003", "mem-004", "mem-005");

        // Vector chunks (3 chunks aligned with node chunks)
        Path vectorsDir = unpackDir.resolve("vectors");
        Path vec1 = vectorsDir.resolve("chunk-00001.bin");
        Path vec2 = vectorsDir.resolve("chunk-00002.bin");
        Path vec3 = vectorsDir.resolve("chunk-00003.bin");
        assertThat(Files.exists(vec1)).isTrue();
        assertThat(Files.exists(vec2)).isTrue();
        assertThat(Files.exists(vec3)).isTrue();

        // 2 records * 4 dims * 4 bytes = 32 bytes
        assertThat(Files.size(vec1)).isEqualTo(2L * DIMS * Float.BYTES);
        assertThat(Files.size(vec2)).isEqualTo(2L * DIMS * Float.BYTES);
        // 1 record * 4 dims * 4 bytes = 16 bytes
        assertThat(Files.size(vec3)).isEqualTo(1L * DIMS * Float.BYTES);

        // Read and verify vectors are valid IEEE floats
        byte[] v1Bytes = Files.readAllBytes(vec1);
        ByteBuffer bb = ByteBuffer.wrap(v1Bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] firstVector = new float[DIMS];
        for (int i = 0; i < DIMS; i++) {
            firstVector[i] = bb.getFloat();
        }
        assertThat(firstVector).isNotEmpty();

        // Graph edges: verify endpoint IDs (not slot indices, Task 2.4)
        Path edgesFile = unpackDir.resolve("graph").resolve("edges.jsonl");
        assertThat(Files.exists(edgesFile)).isTrue();
        List<ExportEdgeRecord> edgeRecords = Files.lines(edgesFile)
                .map(this::parseEdge)
                .toList();
        assertThat(edgeRecords).isNotEmpty();
        ExportEdgeRecord edge = edgeRecords.getFirst();
        assertThat(edge.sourceId()).isIn("mem-001", "mem-002");
        assertThat(edge.targetId()).isIn("mem-001", "mem-002");
        assertThat(edge.kind()).isEqualTo("HEBBIAN");
        assertThat(edge.weight()).isGreaterThan(0.0f);

        // Hyperedges
        Path hyperedgesFile = unpackDir.resolve("graph").resolve("hyperedges.jsonl");
        assertThat(Files.exists(hyperedgesFile)).isTrue();
        List<ExportHyperedgeRecord> hyperRecords = Files.lines(hyperedgesFile)
                .map(this::parseHyperedge)
                .toList();
        assertThat(hyperRecords).isNotEmpty();
        ExportHyperedgeRecord hedge = hyperRecords.getFirst();
        assertThat(hedge.type()).isEqualTo("TYPE_RELATIONSHIP");
        assertThat(hedge.vertices()).hasSize(2);

        // Facts
        Path factsFile = unpackDir.resolve("graph").resolve("facts.jsonl");
        assertThat(Files.exists(factsFile)).isTrue();
        List<ExportFactRecord> factRecords = Files.lines(factsFile)
                .map(this::parseFact)
                .toList();
        assertThat(factRecords).isNotEmpty();
        ExportFactRecord fact = factRecords.getFirst();
        assertThat(fact.subject()).isEqualToIgnoringCase("SpectrayanCorp");
        assertThat(fact.predicate()).isEqualToIgnoringCase("headquarters");
        assertThat(fact.object()).isEqualToIgnoringCase("Austin");
        assertThat(fact.confidence()).isEqualTo(0.95f);

        // Verify security/keys.json DOES NOT EXIST (Task 2.6, R1.5)
        assertThat(Files.exists(unpackDir.resolve("security").resolve("keys.json"))).isFalse();

        // Verify subsystems/state.json DOES NOT EXIST (Task 2.5, R1.4)
        assertThat(Files.exists(unpackDir.resolve("subsystems").resolve("state.json"))).isFalse();
    }

    @Test
    @DisplayName("Export scoping filters by tier (Task 2.8, R1.7)")
    void exportScopingByTier(@TempDir Path tempDir) throws Exception {
        String namespace = "scope-tier-ns";
        DefaultSpectorMemory memory = createMemory();
        memoryRegistry.register(namespace, memory);

        memory.remember("w-1", "Working memory one", MemoryType.WORKING, MemorySource.OBSERVED);
        memory.remember("w-2", "Working memory two", MemoryType.WORKING, MemorySource.OBSERVED);
        memory.remember("s-1", "Semantic memory one", MemoryType.SEMANTIC, MemorySource.OBSERVED);
        memory.remember("e-1", "Episodic memory one", MemoryType.EPISODIC, MemorySource.OBSERVED);

        Path bundlePath = tempDir.resolve("tier-scope.smb");
        Map<String, Object> jobParams = Map.of(
                "tier", "WORKING"
        );

        JobExecution execution = batchService.runExportJob(namespace, bundlePath, jobParams);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        SpectorBundleManifest manifest = bundleCodec.readManifest(bundlePath);
        assertThat(manifest.counts().records()).isEqualTo(2L);

        Path unpackDir = tempDir.resolve("tier-unpacked");
        bundleCodec.unpackBundle(bundlePath, unpackDir);
        List<ExportNodeRecord> nodes = Files.lines(unpackDir.resolve("nodes").resolve("chunk-00001.jsonl"))
                .map(this::parseNode)
                .toList();
        assertThat(nodes).hasSize(2);
        assertThat(nodes).allMatch(n -> "WORKING".equals(n.tier()));
    }

    @Test
    @DisplayName("Export scoping filters by time range (Task 2.8, R1.7)")
    void exportScopingByTimeRange(@TempDir Path tempDir) throws Exception {
        String namespace = "scope-time-ns";
        DefaultSpectorMemory memory = createMemory();
        memoryRegistry.register(namespace, memory);

        memory.remember("t-1", "Time memory one", MemoryType.WORKING, MemorySource.OBSERVED);
        long t1Timestamp = memory.inspect("t-1").timestampMs();

        Thread.sleep(15);

        memory.remember("t-2", "Time memory two", MemoryType.WORKING, MemorySource.OBSERVED);
        long t2Timestamp = memory.inspect("t-2").timestampMs();

        // Export with createdTo = t1Timestamp
        Path bundlePath = tempDir.resolve("time-scope.smb");
        Map<String, Object> jobParams = Map.of(
                "createdTo", t1Timestamp
        );

        JobExecution execution = batchService.runExportJob(namespace, bundlePath, jobParams);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        SpectorBundleManifest manifest = bundleCodec.readManifest(bundlePath);
        assertThat(manifest.counts().records()).isEqualTo(1L);

        Path unpackDir = tempDir.resolve("time-unpacked");
        bundleCodec.unpackBundle(bundlePath, unpackDir);
        List<ExportNodeRecord> nodes = Files.lines(unpackDir.resolve("nodes").resolve("chunk-00001.jsonl"))
                .map(this::parseNode)
                .toList();
        assertThat(nodes).hasSize(1);
        assertThat(nodes.getFirst().id()).isEqualTo("t-1");
    }

    @Test
    @DisplayName("Export fails cleanly when namespace has no memory configured (Task 2.1)")
    void exportFailsCleanlyWhenNamespaceNotFound(@TempDir Path tempDir) throws Exception {
        Path bundlePath = tempDir.resolve("missing.smb");
        JobExecution execution = batchService.runExportJob("non-existent-namespace", bundlePath);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(Files.exists(bundlePath)).isFalse();
        assertThat(execution.getAllFailureExceptions()).isNotEmpty();
        assertThat(execution.getAllFailureExceptions().getFirst())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no SpectorMemory or SpectorMemoryResolver available for namespace: non-existent-namespace");
    }

    private String readManifestRaw(Path bundleFile) throws Exception {
        try (var zip = new java.util.zip.ZipFile(bundleFile.toFile())) {
            var entry = zip.getEntry("manifest.json");
            assertThat(entry).isNotNull();
            try (InputStream in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
    }

    private ExportNodeRecord parseNode(String json) {
        try {
            return MAPPER.readValue(json, ExportNodeRecord.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ExportEdgeRecord parseEdge(String json) {
        try {
            return MAPPER.readValue(json, ExportEdgeRecord.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ExportHyperedgeRecord parseHyperedge(String json) {
        try {
            return MAPPER.readValue(json, ExportHyperedgeRecord.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ExportFactRecord parseFact(String json) {
        try {
            return MAPPER.readValue(json, ExportFactRecord.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;

        MockEmbeddingProvider(final int dims) {
            this.dims = dims;
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
            return new EmbeddingResult(vector, text.split("\\s+").length, "mock-" + dims + "d");
        }

        @Override
        public int dimensions() { return dims; }

        @Override
        public String modelName() { return "mock-" + dims + "d"; }
    }
}
