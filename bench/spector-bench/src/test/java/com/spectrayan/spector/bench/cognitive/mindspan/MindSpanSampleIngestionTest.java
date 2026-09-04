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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.model.EpisodeRecord;
import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IngestionHints;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates ingestion of a 10-record sample (5 episodic, 5 semantic) from MindSpan into a new directory,
 * inspecting off-heap headers, memory index fidelity, and retrieval capabilities.
 */
public class MindSpanSampleIngestionTest {

    private static final Logger log = LoggerFactory.getLogger(MindSpanSampleIngestionTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("Sample 10 MindSpan records (episodic + semantic) ingestion and verification")
    public void testSample10Ingestion() throws Exception {
        Path datasetDir = Paths.get("d:/git/spector-datasets/mindspan/data");
        Path corpusFile = datasetDir.resolve("corpus.jsonl");
        Path cacheFile = datasetDir.resolve("embeddings.bin");
        Path newOutputDir = Paths.get("d:/git/spector-datasets/mindspan/results/test-ingest-sample10");

        assertTrue(Files.exists(corpusFile), "Corpus file must exist at: " + corpusFile);
        assertTrue(Files.exists(cacheFile), "Embeddings cache must exist at: " + cacheFile);

        // 1. Clean previous run in new output folder if present
        if (Files.exists(newOutputDir)) {
            try (var s = Files.walk(newOutputDir)) {
                s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        Files.createDirectories(newOutputDir);

        // 2. Load first 10 records
        List<BenchmarkCorpusRecord> sampleRecords = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(corpusFile.toFile(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && sampleRecords.size() < 10) {
                line = line.trim();
                if (!line.isEmpty()) {
                    sampleRecords.add(MAPPER.readValue(line, BenchmarkCorpusRecord.class));
                }
            }
        }
        assertEquals(10, sampleRecords.size(), "Must have loaded 10 sample records");

        long episodicCount = sampleRecords.stream().filter(r -> r.memoryType() == MemoryType.EPISODIC).count();
        long semanticCount = sampleRecords.stream().filter(r -> r.memoryType() == MemoryType.SEMANTIC).count();
        log.info("Loaded {} records ({} EPISODIC, {} SEMANTIC)", sampleRecords.size(), episodicCount, semanticCount);
        assertEquals(5, episodicCount, "First 10 records must contain 5 EPISODIC records");
        assertEquals(5, semanticCount, "First 10 records must contain 5 SEMANTIC records");

        // 3. Setup SpectorMemory backed by CachedEmbeddingProvider
        EmbeddingProvider fallback = OllamaEmbeddingProvider.createDefault();
        EmbeddingProvider cachedEmbedder = new CachedEmbeddingProvider(fallback, cacheFile);

        log.info("Ingesting 10 records into new persistent store at: {}", newOutputDir);
        try (SpectorMemory memory = SpectorMemoryBuilder.create()
                .dimensions(768)
                .embeddingProvider(cachedEmbedder)
                .persistence(newOutputDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .episodicPartitionCapacity(35_000)
                .semanticCapacity(20_000)
                .build()) {

            for (BenchmarkCorpusRecord record : sampleRecords) {
                String text = record.text();
                long ts = record.timestampMs() > 0 ? record.timestampMs() : System.currentTimeMillis();

                MemorySource source = MemorySource.OBSERVED;
                if (text != null) {
                    if (text.startsWith("user:") || text.startsWith("User:")) {
                        source = MemorySource.USER_STATED;
                    } else if (text.startsWith("assistant:") || text.startsWith("Jarvis:")) {
                        source = MemorySource.INFERRED;
                    }
                }

                IngestionHints hints = new IngestionHints(
                        record.interest(), record.challenge(), record.urgency(),
                        record.valence(), (byte) record.arousal()
                );
                IngestionContext ctx = IngestionContext.builder()
                        .hints(hints)
                        .overrideTimestampMs(ts)
                        .build();
                List<String> tags = record.synapticTags() != null ? record.synapticTags() : List.of();

                log.info("  -> Ingesting [{}] type={} title='{}'", record.id(), record.memoryType(), record.title());
                memory.remember(
                        record.id(),
                        record.text(),
                        record.memoryType(),
                        source,
                        ctx,
                        tags.toArray(String[]::new)
                );
            }

            // 4. Ingested Data Verification
            assertEquals(10, memory.totalMemories(), "Total memories must be 10");

            MemoryIndex index = memory.admin().index();
            assertNotNull(index, "Index must be present");
            assertEquals(10, index.size(), "Index size must be 10");

            var router = memory.admin().cognitiveRouter();
            assertNotNull(router, "CognitiveRouter must be present");
            EpisodicMemory episodic = router.episodic();
            assertNotNull(episodic, "EpisodicMemory store must be present");
            assertEquals(5, episodic.visibleCount(), "Episodic visibleCount must be 5");

            for (BenchmarkCorpusRecord record : sampleRecords) {
                MemoryIndex.MemoryLocation loc = index.locate(record.id());
                assertNotNull(loc, "Memory ID " + record.id() + " must be located in index");
                assertEquals(record.memoryType(), loc.type(), "Memory type must match for " + record.id());

                String indexedText = index.text(record.id());
                assertNotNull(indexedText, "Indexed text must not be null for " + record.id());
                assertEquals(record.text(), indexedText, "Indexed text must match for " + record.id());

                if (loc.type() == MemoryType.EPISODIC) {
                    EpisodeRecord turn = episodic.readTurn(loc.offset(), true);
                    assertNotNull(turn, "Turn must be readable from episodic memory for " + record.id());
                    assertNotNull(turn.body(), "Turn body must be readable for " + record.id());
                    String turnText = new String(turn.body(), StandardCharsets.UTF_8);
                    assertEquals(record.text(), turnText, "Turn body text must match for " + record.id());

                    EncodingHeader header = episodic.readHeader(loc.offset());
                    assertNotNull(header, "Episodic header must be readable for " + record.id());
                    assertEquals(record.timestampMs(), header.timestampMs(), "Timestamp must match for " + record.id());
                    assertEquals((byte) record.arousal(), header.arousal(), "Arousal must match for " + record.id());
                    log.info("✔ EPISODIC [{}] verified: offset={}, timestamp={}, arousal={}, bodyLength={}",
                            record.id(), loc.offset(), header.timestampMs(), header.arousal(), turn.body().length);
                } else if (loc.type() == MemoryType.SEMANTIC) {
                    var segment = router.segmentFor(MemoryType.SEMANTIC);
                    var layout = router.layoutFor(MemoryType.SEMANTIC);
                    EncodingHeader header = layout.readHeader(segment, loc.offset());
                    assertNotNull(header, "Semantic header must be readable for " + record.id());
                    assertEquals(record.timestampMs(), header.timestampMs(), "Timestamp must match for " + record.id());
                    assertEquals((byte) record.arousal(), header.arousal(), "Arousal must match for " + record.id());
                    log.info("✔ SEMANTIC [{}] verified: offset={}, timestamp={}, arousal={}",
                            record.id(), loc.offset(), header.timestampMs(), header.arousal());
                }
            }

            // 5. Test Recall on both EPISODIC and SEMANTIC traces
            List<CognitiveResult> results = memory.recall("Elgin watch", RecallOptions.builder().topK(5).build());
            log.info("Recall results for 'Elgin watch': count={}", results.size());
            for (CognitiveResult r : results) {
                log.info("   hit: id={}, type={}, score={}, text='{}'", r.id(), r.memoryType(), r.score(),
                        r.text().length() > 60 ? r.text().substring(0, 60) + "..." : r.text());
            }
            assertFalse(results.isEmpty(), "Recall for 'Elgin watch' must yield results");
            boolean foundEpisodic = results.stream().anyMatch(r -> r.memoryType() == MemoryType.EPISODIC);
            boolean foundSemantic = results.stream().anyMatch(r -> r.memoryType() == MemoryType.SEMANTIC);
            assertTrue(foundEpisodic, "Must retrieve episodic memory in results");
            assertTrue(foundSemantic, "Must retrieve semantic memory in results");
        }

        // 6. Verify Reopening from Disk
        log.info("Reopening SpectorMemory from new persistent store to verify on-disk bundle integrity...");
        try (SpectorMemory reopened = SpectorMemoryBuilder.create()
                .dimensions(768)
                .embeddingProvider(cachedEmbedder)
                .persistence(newOutputDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .episodicPartitionCapacity(35_000)
                .semanticCapacity(20_000)
                .build()) {

            assertEquals(10, reopened.totalMemories(), "Reopened store must contain 10 total memories");
            assertEquals(10, reopened.admin().index().size(), "Reopened index must contain 10 entries");
            log.info("✔ Reopened store verified successfully: totalMemories={}", reopened.totalMemories());
        }
    }
}
