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
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.GraphEnrichmentDaemon;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.pathway.reflect.daemon.CircadianPolicy;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("mindspan-synapse-reextract")
public class MindSpanSynapseReextractionTest {

    private static final Logger log = LoggerFactory.getLogger(MindSpanSynapseReextractionTest.class);

    private Path resolveDatasetDir() {
        String prop = System.getProperty("datasetDir");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop).toAbsolutePath().normalize();
        }
        String env = System.getenv("MINDSPAN_DATASET_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env).toAbsolutePath().normalize();
        }
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
    void testSynapseGraphReextraction() throws Exception {
        Path datasetDir = resolveDatasetDir();
        Path naturalMemoryDir = datasetDir.resolve("results").resolve("ingested-memory");
        Path cacheFile = datasetDir.resolve("embeddings.bin");

        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(naturalMemoryDir),
                "MindSpan ingested memory dir must exist; skipping in CI: " + naturalMemoryDir);

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("spector.provider.google.api-key");
        }
        if (apiKey == null || apiKey.isBlank()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "GEMINI_API_KEY environment variable not set; skipping live LLM test");
        }

        GoogleProviderFactory googleFactory = new GoogleProviderFactory();
        ProviderConfig genConfig = new ProviderConfig(
                "google-generation", "google",
                "gemini-3.1-flash-lite", apiKey, "", 0,
                Map.of("temperature", "0.2", "maxOutputTokens", "1024", "insecure", "true")
        );
        LlmProvider llm = googleFactory.createGenerationProvider(genConfig)
                .orElseThrow(() -> new IllegalStateException("Failed to instantiate Google Gemini LLM Provider"));

        EmbeddingProvider raw = OllamaEmbeddingProvider.createDefault();
        EmbeddingProvider embedder = new CachedEmbeddingProvider(raw, cacheFile);

        SpectorProperties props = SpectorProperties.builder().build();
        MemoryProperties memProps = SpectorConfigFactory.memoryProperties(props);

        SpectorMemory memory = SpectorMemoryBuilder.create()
                .fromProperties(memProps)
                .dimensions(768)
                .embeddingProvider(embedder)
                .llmProvider(llm)
                .entityExtractionMode(EntityExtractionMode.LLM)
                .persistence(naturalMemoryDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .episodicPartitionCapacity(35_000)
                .semanticCapacity(20_000)
                .circadianPolicy(CircadianPolicy.builder().volumeTrigger(Integer.MAX_VALUE).build())
                .build();

        try {
            System.out.println("\n==========================================================================");
            System.out.println("  🧠 SYNAPSE / GRAPH ENRICHMENT DAEMON RE-EXTRACTION PIPELINE TEST       ");
            System.out.println("==========================================================================");

            GraphEnrichmentDaemon enricher = memory.admin().graphEnricher();
            assertNotNull(enricher, "GraphEnrichmentDaemon must be wired when EntityExtractionMode.LLM is active");

            EntityDirectory dirBefore = memory.admin().entityDirectory();
            TemporalKnowledgeGraph tkgBefore = memory.admin().temporalKnowledgeGraph();

            int initialEntities = dirBefore != null ? dirBefore.entityCount() : 0;
            int initialFacts = tkgBefore != null ? tkgBefore.factCount() : 0;

            System.out.printf("Before Re-Extraction: %d entities, %d temporal facts\n", initialEntities, initialFacts);

            try {
                System.out.println("Testing llm.generate directly...");
                String testGen = llm.generate("Hello! Please reply with 'Gemini OK'");
                System.out.println("Direct LLM response: " + testGen);
            } catch (Exception e) {
                System.err.println("Direct LLM invocation FAILED: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
            }

            com.spectrayan.spector.memory.graph.LlmEntityExtractor directExtractor =
                    new com.spectrayan.spector.memory.graph.LlmEntityExtractor(llm);
            System.out.println("Direct LlmEntityExtractor isAvailable: " + directExtractor.isAvailable());
            var sampleEntities = directExtractor.extract("bio-0001",
                    "Great-grandpa Arthur just handed me his old Elgin watch. He said he carried it through the whole war in France.");
            System.out.println("Direct extraction result on bio-0001: " + sampleEntities);

            // Execute re-extraction batch specifically targeting SEMANTIC memories using the Synapse SPI pattern
            System.out.println("Invoking enricher.reextractBatch(5, MemoryType.SEMANTIC)...");
            int reextracted = enricher.reextractBatch(5, com.spectrayan.spector.memory.model.MemoryType.SEMANTIC);
            System.out.printf("Successfully re-extracted %d SEMANTIC memories via Gemini LLM (lastError: %s)\n",
                    reextracted, enricher.stats().lastError());

            int entitiesAfter = dirBefore != null ? dirBefore.entityCount() : 0;
            int factsAfter = tkgBefore != null ? tkgBefore.factCount() : 0;

            System.out.printf("After Re-Extraction:  %d entities, %d temporal facts\n", entitiesAfter, factsAfter);

            if (dirBefore != null && entitiesAfter > 0) {
                System.out.println("\nSample Interned Entities in EntityDirectory:");
                for (int i = 0; i < Math.min(10, entitiesAfter); i++) {
                    System.out.printf("  [%d] %s (%s) -> %d memories\n",
                            i, dirBefore.entityName(i), dirBefore.entityType(i), dirBefore.memoryRefCount(i));
                }
            }

            assertTrue(reextracted > 0, "At least 1 memory must be re-extracted");
            assertTrue(entitiesAfter > initialEntities, "Entity count must increase after re-extraction");

            System.out.println("==========================================================================\n");

        } finally {
            memory.close();
        }
    }
}
