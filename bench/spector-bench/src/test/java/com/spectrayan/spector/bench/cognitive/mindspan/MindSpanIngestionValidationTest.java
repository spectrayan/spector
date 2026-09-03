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
package com.spectrayan.spector.bench.cognitive.mindspan;

import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.model.TextSearchMode;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.hebbian.HebbianEdge;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.graph.temporal.TemporalChainMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.reflect.daemon.CircadianPolicy;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("mindspan-validation")
public class MindSpanIngestionValidationTest {

    private static final Logger log = LoggerFactory.getLogger(MindSpanIngestionValidationTest.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Path resolveDatasetDir() {
        String prop = System.getProperty("datasetDir");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop).toAbsolutePath().normalize();
        }
        String env = System.getenv("MINDSPAN_DATASET_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env).toAbsolutePath().normalize();
        }
        // Ascend up from current directory to locate spector-datasets/mindspan
        Path curr = Paths.get(".").toAbsolutePath().normalize();
        while (curr != null) {
            Path candidate = curr.resolve("spector-datasets").resolve("mindspan");
            if (Files.exists(candidate)) {
                return candidate.normalize();
            }
            Path sibling = curr.resolve("..").resolve("spector-datasets").resolve("mindspan").normalize();
            if (Files.exists(sibling)) {
                return sibling;
            }
            curr = curr.getParent();
        }
        return Paths.get("..", "spector-datasets", "mindspan").toAbsolutePath().normalize();
    }

    @Test
    void validateIngestedMindSpanMemory() throws Exception {
        Path datasetDir = resolveDatasetDir();
        Path corpusFile = datasetDir.resolve("data").resolve("corpus.jsonl");
        if (!Files.exists(corpusFile)) {
            corpusFile = datasetDir.resolve("corpus.jsonl");
        }
        Path naturalMemoryDir = datasetDir.resolve("results").resolve("ingested-memory");
        Path cacheFile = datasetDir.resolve("embeddings.bin");

        assertTrue(Files.exists(corpusFile), "Corpus file must exist: " + corpusFile);
        assertTrue(Files.exists(naturalMemoryDir), "Ingested memory dir must exist: " + naturalMemoryDir);

        // Load JSON corpus
        Map<String, BenchmarkCorpusRecord> corpusMap = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(corpusFile.toFile(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    BenchmarkCorpusRecord r = jsonMapper.readValue(line, BenchmarkCorpusRecord.class);
                    corpusMap.put(r.id(), r);
                }
            }
        }
        int expectedCorpusSize = corpusMap.size();

        // Boot SpectorMemory in Read-Only Mode against ingested-memory
        EmbeddingProvider raw = OllamaEmbeddingProvider.createDefault();
        EmbeddingProvider embedder = new CachedEmbeddingProvider(raw, cacheFile);

        SpectorProperties props = SpectorProperties.builder().build();
        MemoryProperties memProps = SpectorConfigFactory.memoryProperties(props);

        SpectorMemory memory = SpectorMemoryBuilder.create()
                .fromProperties(memProps)
                .dimensions(768)
                .embeddingProvider(embedder)
                .entityExtractionMode(com.spectrayan.spector.memory.graph.EntityExtractionMode.CUSTOM)
                .entityExtractor(com.spectrayan.spector.memory.graph.NoOpEntityExtractor.INSTANCE)
                .persistence(naturalMemoryDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .episodicPartitionCapacity(35_000)
                .semanticCapacity(20_000)
                .circadianPolicy(CircadianPolicy.builder().volumeTrigger(Integer.MAX_VALUE).build())
                .build();

        try {
            System.out.println("\n==========================================================================");
            System.out.println("  🧠 SPECTOR MEMORY — MINDSPAN INGESTION INTEGRITY VALIDATION AUDIT      ");
            System.out.printf("  Dataset Location: %s\n", datasetDir);
            System.out.printf("  Ingested Mmap:    %s\n", naturalMemoryDir);
            System.out.println("==========================================================================");

            // 1. Total Memories & Index Fidelity
            int totalMemories = memory.totalMemories();
            int indexSize = memory.admin().index().size();
            Map<String, MemoryIndex.MemoryLocation> locationMap = memory.admin().index().locationMap();

            Map<MemoryType, Integer> typeCounts = new EnumMap<>(MemoryType.class);
            int validTextCount = 0;
            int missingTextCount = 0;

            for (Map.Entry<String, MemoryIndex.MemoryLocation> entry : locationMap.entrySet()) {
                String id = entry.getKey();
                MemoryIndex.MemoryLocation loc = entry.getValue();
                typeCounts.merge(loc.type(), 1, Integer::sum);

                String text = memory.admin().index().text(id);
                if (text != null && !text.isBlank()) {
                    validTextCount++;
                } else {
                    missingTextCount++;
                }
            }

            System.out.println("\n1. TOTAL MEMORIES AUDIT:");
            System.out.printf("   - JSON Corpus Records:       %,d\n", expectedCorpusSize);
            System.out.printf("   - Spector Total Memories:    %,d\n", totalMemories);
            System.out.printf("   - MemoryIndex Size:          %,d\n", indexSize);
            System.out.printf("   - Valid Text Retrieved:      %,d (%.2f%%)\n", validTextCount, (validTextCount * 100.0) / Math.max(1, totalMemories));
            System.out.printf("   - Missing / Null Text:       %,d\n", missingTextCount);
            System.out.printf("   - Episodic Memories:         %,d\n", typeCounts.getOrDefault(MemoryType.EPISODIC, 0));
            System.out.printf("   - Semantic Memories:         %,d\n", typeCounts.getOrDefault(MemoryType.SEMANTIC, 0));
            System.out.printf("   - Procedural Memories:       %,d\n", typeCounts.getOrDefault(MemoryType.PROCEDURAL, 0));
            System.out.printf("   - Working Memories:          %,d\n", typeCounts.getOrDefault(MemoryType.WORKING, 0));

            assertEquals(expectedCorpusSize, indexSize, "All corpus records must be indexed in Spector MemoryIndex");
            assertEquals(0, missingTextCount, "No records should have null text");

            // 2. Hebbian Associative Graph
            HebbianGraphBase hebbian = memory.admin().graph() != null ? memory.admin().graph().rawHebbianGraph() : null;
            int hebbianCapacity = hebbian != null ? hebbian.capacity() : 0;
            int hebbianEdges = hebbian != null ? hebbian.totalEdges() : 0;

            System.out.println("\n2. HEBBIAN ASSOCIATIVE GRAPH AUDIT:");
            System.out.printf("   - Graph Status:              %s\n", hebbian != null ? "ONLINE (CSR)" : "OFFLINE");
            System.out.printf("   - Graph Capacity:            %,d nodes\n", hebbianCapacity);
            System.out.printf("   - Total Associated Edges:    %,d\n", hebbianEdges);

            assertTrue(hebbianEdges > 0, "Hebbian graph must contain co-activation edges");

            // 3. Temporal Chains
            TemporalChainMemory tc = memory.admin().graph() != null ? memory.admin().graph().rawTemporalChain() : null;
            System.out.println("\n3. TEMPORAL CHAINS AUDIT:");
            System.out.printf("   - Temporal Chain Status:     %s\n", tc != null ? "ONLINE (Mmap Doubly-Linked)" : "OFFLINE");

            // 4. Lexical BM25 Search
            List<CognitiveResult> bm25Hits = memory.recall("Elgin watch", RecallOptions.builder()
                    .textSearchMode(TextSearchMode.BM25_ONLY)
                    .topK(5)
                    .build());
            System.out.println("\n4. SYNAPTIC TAGS & LEXICAL INDEX AUDIT:");
            System.out.printf("   - BM25 Test Query 'Elgin watch' hits: %,d\n", bm25Hits.size());
            if (!bm25Hits.isEmpty()) {
                System.out.printf("   - Top BM25 Hit:              [%s] \"%s\"\n",
                        bm25Hits.get(0).id(), bm25Hits.get(0).text().substring(0, Math.min(60, bm25Hits.get(0).text().length())) + "...");
            }

            // 5. Entities & Knowledge Graphs
            EntityDirectory dir = memory.admin().entityDirectory();
            TemporalKnowledgeGraph tkg = memory.admin().temporalKnowledgeGraph();
            HyperEntityGraphMemory hyper = memory.admin().hyperEntityGraph();

            int entityCount = dir != null ? dir.entityCount() : 0;
            int tkgFacts = tkg != null ? tkg.factCount() : 0;
            int hyperEdges = hyper != null ? hyper.totalHyperedges() : 0;

            System.out.println("\n5. ENTITY & KNOWLEDGE GRAPH AUDIT:");
            System.out.printf("   - Entity Directory Count:    %,d entities\n", entityCount);
            System.out.printf("   - TKG Temporal Facts:        %,d facts\n", tkgFacts);
            System.out.printf("   - HyperEntityGraph Edges:    %,d hyperedges\n", hyperEdges);

            // 6. Sample Ground-Truth Records Fidelity Check
            System.out.println("\n6. SAMPLE KEY RECORDS FIDELITY VERIFICATION:");
            String[] testKeys = {
                    "bio-0001",                      // Elgin pocket watch
                    "bio-0007",                      // Bourgeois Pig cafe
                    "bio-marriage_parenthood-0603",  // Ethan birth
                    "bio-maturity_transition-0919",  // Daniel pilot stories
                    "bio-0017"                       // Robert Miller passing
            };

            for (String key : testKeys) {
                var loc = memory.admin().index().locate(key);
                String storedText = memory.admin().index().text(key);
                String[] tags = memory.admin().index().tags(key);
                BenchmarkCorpusRecord orig = corpusMap.get(key);

                System.out.printf("   ► Record [%s]:\n", key);
                System.out.printf("     - Located:                 %s (type=%s, slot=%d, offset=%d)\n",
                        loc != null ? "YES" : "MISSING", loc != null ? loc.type() : "N/A",
                        loc != null ? loc.graphSlot() : -1, loc != null ? loc.offset() : -1);
                System.out.printf("     - Synaptic Tags:           %s\n", tags != null ? Arrays.toString(tags) : "[]");
                System.out.printf("     - Text Length:             %d chars (exp: %d)\n",
                        storedText != null ? storedText.length() : 0, orig != null ? orig.text().length() : 0);
                System.out.printf("     - Text Preview:            \"%s\"\n",
                        storedText != null ? storedText.substring(0, Math.min(65, storedText.length())) + "..." : "NULL");

                assertNotNull(loc, "Sample record " + key + " must exist in index");
                assertNotNull(storedText, "Sample record " + key + " must have valid text");
                if (orig != null) {
                    assertEquals(orig.text(), storedText, "Stored text must match source JSON verbatim");
                }
            }

            System.out.println("==========================================================================\n");

        } finally {
            memory.close();
        }
    }
}
