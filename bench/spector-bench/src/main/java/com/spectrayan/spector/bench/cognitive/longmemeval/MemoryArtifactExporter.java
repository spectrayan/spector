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
package com.spectrayan.spector.bench.cognitive.longmemeval;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.BenchmarkSetup;
import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.DatasetLoader;
import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.hebbian.HebbianEdge;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalChainMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Extracts memories, entity graph structures, temporal chains, and Hebbian edges
 * from an ingested SpectorMemory instance into standalone inspection files.
 */
public final class MemoryArtifactExporter {

    private static final Logger log = LoggerFactory.getLogger(MemoryArtifactExporter.class);
    private static final ObjectMapper mapper = JsonMapper.builder().build();

    private final Path datasetDir;
    private final Path exportDir;

    public MemoryArtifactExporter(Path datasetDir, Path exportDir) {
        this.datasetDir = datasetDir;
        this.exportDir = exportDir;
    }

    public void exportAll() throws IOException {
        Files.createDirectories(exportDir);

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);

        try (BenchmarkSetup setup = new BenchmarkSetup();
             EmbeddingProvider raw = OllamaEmbeddingProvider.createDefault();
             EmbeddingProvider embedder = new CachedEmbeddingProvider(raw, datasetDir.resolve("embeddings.bin"))) {

            SpectorMemory memory = setup.createMemoryInstance(dataset, embedder, datasetDir);
            log.info("SpectorMemory instance loaded. Starting artifact extraction to {}", exportDir);

            // Reconstruct reverse mapping: slot -> memoryId
            Map<Integer, String> slotToMemoryId = new LinkedHashMap<>();
            for (BenchmarkCorpusRecord rec : dataset.corpus()) {
                var loc = memory.admin().index().locate(rec.id());
                if (loc != null) {
                    slotToMemoryId.put(loc.graphSlot(), rec.id());
                }
            }

            exportMemories(memory, dataset, exportDir.resolve("extracted_memories.jsonl"));
            exportEntities(memory, slotToMemoryId, exportDir.resolve("entities.jsonl"));
            exportTemporalChains(memory, dataset, slotToMemoryId, exportDir.resolve("temporal_chains.jsonl"));
            exportHebbianEdges(memory, slotToMemoryId, exportDir.resolve("hebbian_edges.jsonl"));

            log.info("Artifact export completed successfully.");
        }
    }

    private void exportMemories(SpectorMemory memory, LoadedDataset dataset, Path target) throws IOException {
        Map<String, BenchmarkCorpusRecord> corpusMap = new LinkedHashMap<>();
        for (BenchmarkCorpusRecord rec : dataset.corpus()) {
            corpusMap.put(rec.id(), rec);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(target)) {
            for (Map.Entry<String, MemoryIndex.MemoryLocation> entry : memory.admin().index().locationMap().entrySet()) {
                String memId = entry.getKey();
                MemoryIndex.MemoryLocation loc = entry.getValue();
                BenchmarkCorpusRecord orig = corpusMap.get(memId);

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("id", memId);
                out.put("memoryType", loc.type().name());
                out.put("graphSlot", loc.graphSlot());
                out.put("offset", loc.offset());

                if (orig != null) {
                    out.put("text", orig.text());
                    out.put("timestampMs", orig.timestampMs());
                    out.put("valence", orig.valence());
                    out.put("arousal", orig.arousal());
                    out.put("importance", orig.importance());
                    out.put("synapticTags", orig.synapticTags());
                    out.put("sessionId", orig.sessionId());
                }

                writer.write(mapper.writeValueAsString(out));
                writer.newLine();
            }
        }
        log.info("Exported {} memories to {}", memory.admin().index().size(), target);
    }

    private void exportEntities(SpectorMemory memory, Map<Integer, String> slotToId, Path target) throws IOException {
        EntityDirectory dir = memory.admin().entityDirectory();
        if (dir == null || dir.entityCount() == 0) {
            log.info("EntityDirectory is empty  --  writing empty entities.jsonl");
            Files.writeString(target, "");
            return;
        }

        int totalEntities = dir.entityCount();
        try (BufferedWriter writer = Files.newBufferedWriter(target)) {
            for (int i = 0; i < totalEntities; i++) {
                String name = dir.entityName(i);
                if (name == null || name.isBlank()) continue;
                String type = dir.entityType(i);
                int refCount = dir.memoryRefCount(i);
                List<String> adjacentMemIds = new ArrayList<>();
                for (int r = 0; r < refCount; r++) {
                    int slot = dir.memoryRefAt(i, r);
                    String mid = slotToId.get(slot);
                    if (mid != null) {
                        adjacentMemIds.add(mid);
                    }
                }

                Map<String, Object> from = Map.of("name", name, "type", type != null ? type : "CONCEPT");
                Map<String, Object> to = Map.of("name", name + "_context", "type", "CONTEXT");
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("fromEntity", from);
                out.put("toEntity", to);
                out.put("relationType", "RELATED_TO");
                out.put("sourceMemoryIds", adjacentMemIds);

                writer.write(mapper.writeValueAsString(out));
                writer.newLine();
            }
        }
        log.info("Exported {} entities to {}", totalEntities, target);
    }

    private void exportTemporalChains(SpectorMemory memory, LoadedDataset dataset, Map<Integer, String> slotToId, Path target) throws IOException {
        Map<String, List<String>> sessionToMems = new LinkedHashMap<>();
        for (BenchmarkCorpusRecord rec : dataset.corpus()) {
            if (rec.sessionId() != null && !rec.sessionId().isBlank()) {
                sessionToMems.computeIfAbsent(rec.sessionId(), k -> new ArrayList<>()).add(rec.id());
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(target)) {
            for (Map.Entry<String, List<String>> entry : sessionToMems.entrySet()) {
                if (entry.getValue().size() >= 2) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("sessionId", entry.getKey());
                    out.put("orderedMemoryIds", entry.getValue());

                    writer.write(mapper.writeValueAsString(out));
                    writer.newLine();
                }
            }
        }
        log.info("Exported {} session temporal chains to {}", sessionToMems.size(), target);
    }

    private void exportHebbianEdges(SpectorMemory memory, Map<Integer, String> slotToId, Path target) throws IOException {
        var rawHg = memory.admin().graph() != null ? memory.admin().graph().rawHebbianGraph() : null;
        if (!(rawHg instanceof HebbianGraphMemory hg)) {
            log.warn("HebbianGraphMemory not available  --  writing empty hebbian edges file.");
            Files.writeString(target, "");
            return;
        }

        int capacity = hg.capacity();
        int edgeCount = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(target)) {
            for (int slot = 0; slot < capacity; slot++) {
                List<HebbianEdge> neighbors = hg.neighbors(slot);

                if (neighbors != null && !neighbors.isEmpty()) {
                    String memA = slotToId.get(slot);
                    if (memA == null) continue;

                    for (HebbianEdge edge : neighbors) {
                        String memB = slotToId.get(edge.neighborIndex());
                        if (memB == null || memA.equals(memB)) continue;

                        edgeCount++;
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("memoryIdA", memA);
                        out.put("memoryIdB", memB);
                        out.put("coActivationCount", (int) Math.max(1, edge.weight()));

                        writer.write(mapper.writeValueAsString(out));
                        writer.newLine();
                    }
                }
            }
        }
        log.info("Exported {} Hebbian edges to {}", edgeCount, target);
    }
}
