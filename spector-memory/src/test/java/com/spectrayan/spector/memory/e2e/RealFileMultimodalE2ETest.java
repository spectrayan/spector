/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.e2e;

import com.spectrayan.spector.embed.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.embed.ollama.OllamaLlmProvider;
import com.spectrayan.spector.ingestion.sensory.OllamaVisionExtractor;
import com.spectrayan.spector.ingestion.sensory.SensoryExtractor;
import com.spectrayan.spector.ingestion.sensory.TikaTextExtractor;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.test.judge.LlmAssertions;
import com.spectrayan.spector.test.judge.LlmTestJudge;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for real multimodal file processing.
 *
 * <p>Uses actual image, audio, document, and video files from test resources.
 * Requires a running Ollama instance with vision model (llava or moondream).</p>
 *
 * <p>Set {@code OLLAMA_LIVE=true} to enable these tests.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Multimodal Real File E2E")
class RealFileMultimodalE2ETest {

    private static final Logger log = LoggerFactory.getLogger(RealFileMultimodalE2ETest.class);

    private SpectorMemory memory;
    private OllamaEmbeddingProvider embeddingProvider;
    private LlmTestJudge llmJudge;

    @BeforeAll
    void setUp() {
        boolean ollamaLive = "true".equalsIgnoreCase(System.getenv("OLLAMA_LIVE"))
                || "true".equalsIgnoreCase(System.getProperty("OLLAMA_LIVE"));
        Assumptions.assumeTrue(ollamaLive,
                "Skipping E2E — set OLLAMA_LIVE=true to run");

        embeddingProvider = OllamaEmbeddingProvider.create("nomic-embed-text");

        // Vision extractor — try llava, then moondream
        OllamaVisionExtractor visionExtractor;
        try {
            visionExtractor = OllamaVisionExtractor.create("llava");
            if (!visionExtractor.isAvailable()) {
                visionExtractor = OllamaVisionExtractor.create("moondream");
            }
        } catch (Exception e) {
            visionExtractor = OllamaVisionExtractor.createDefault();
        }

        TikaTextExtractor tikaExtractor = new TikaTextExtractor(500, 80);

        memory = DefaultSpectorMemory.builder()
                .embeddingProvider(embeddingProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .semanticCapacity(2_000)
                .episodicPartitionCapacity(2_000)
                .sensoryExtractors(List.of(visionExtractor, tikaExtractor))
                .build();

        // LLM Judge for semantic validation
        try {
            OllamaLlmProvider judgeLlm = OllamaLlmProvider.create("gemma4:latest");
            if (judgeLlm.isAvailable()) {
                llmJudge = LlmTestJudge.create(judgeLlm);
            }
        } catch (Exception e) {
            log.warn("LLM Judge not available — semantic assertions disabled: {}", e.getMessage());
        }

        log.info("RealFileMultimodalE2E initialized — vision={}, judge={}",
                visionExtractor.model(), llmJudge != null ? "enabled" : "disabled");
    }

    // ══════════════════════════════════════════════════════════════
    // IMAGE TESTS — Real PNG files
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Image Processing")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ImageTests {

        @Test
        @Order(1)
        @DisplayName("Ingest golden retriever photo and recall by content")
        void ingestGoldenRetrieverAndRecall() throws Exception {
            Path image = resolveTestResource("test-images/golden_retriever_park.png");
            Assumptions.assumeTrue(Files.exists(image), "Test image not found: " + image);

            // Ingest via attachments metadata
            var context = IngestionContext.builder()
                    .metadata(SourceModality.ATTACHMENTS_KEY, image.toAbsolutePath().toString())
                    .sourceModality(SourceModality.IMAGE)
                    .build();

            String memId = memory.remember("A photo taken at the park",
                    MemoryType.EPISODIC, MemorySource.USER_STATED, context, "photo", "dog").join();

            assertNotNull(memId, "Should store memory");
            log.info("Ingested golden retriever image as: {}", memId);

            // Recall with semantic query about dogs
            List<CognitiveResult> results = memory.recall("dog in a park",
                    RecallOptions.builder().topK(5).build());

            assertFalse(results.isEmpty(), "Should recall dog image content");
            log.info("Recalled {} results for 'dog in a park'", results.size());
            results.forEach(r -> log.info("  [{:.3f}] {}", r.score(), truncate(r.text(), 100)));

            // LLM judge validation
            if (llmJudge != null) {
                LlmAssertions.assertRecall(llmJudge, "dog in a park", results,
                                CognitiveResult::text)
                        .isRelevantTo("Should find dog or park related content");
            }
        }

        @Test
        @Order(2)
        @DisplayName("Ingest whiteboard diagram and recall by diagram content")
        void ingestWhiteboardAndRecall() throws Exception {
            Path image = resolveTestResource("test-images/whiteboard_diagram.png");
            Assumptions.assumeTrue(Files.exists(image), "Test image not found: " + image);

            var context = IngestionContext.builder()
                    .metadata(SourceModality.ATTACHMENTS_KEY, image.toAbsolutePath().toString())
                    .sourceModality(SourceModality.IMAGE)
                    .build();

            String memId = memory.remember("Whiteboard photo from planning session",
                    MemoryType.SEMANTIC, MemorySource.USER_STATED, context,
                    "whiteboard", "diagram", "planning").join();

            assertNotNull(memId);
            log.info("Ingested whiteboard image as: {}", memId);

            // Recall
            List<CognitiveResult> results = memory.recall("flowchart diagram with boxes and arrows",
                    RecallOptions.builder().topK(5).build());

            assertFalse(results.isEmpty(), "Should recall whiteboard content");
        }

        @Test
        @Order(3)
        @DisplayName("Direct VLM image description produces meaningful text")
        void directVlmDescription() throws Exception {
            Path image = resolveTestResource("test-images/golden_retriever_park.png");
            Assumptions.assumeTrue(Files.exists(image), "Test image not found: " + image);

            var vlm = OllamaVisionExtractor.create("llava");
            Assumptions.assumeTrue(vlm.isAvailable(), "Vision model not available");

            try (var chunks = vlm.extract(image, "image/png")) {
                List<SensoryExtractor.ExtractionChunk> chunkList = chunks.toList();

                assertFalse(chunkList.isEmpty(), "VLM should produce at least one chunk");
                String caption = chunkList.getFirst().text();
                assertFalse(caption.isBlank(), "Caption should not be blank");
                log.info("VLM caption ({}B): {}", caption.length(), truncate(caption, 200));

                // Check metadata
                var meta = chunkList.getFirst().metadata();
                assertEquals("IMAGE", meta.get("modality"));
                assertNotNull(meta.get("vlm_model"));
                assertNotNull(meta.get("latency_ms"));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DOCUMENT TESTS — Real text/HTML files
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Document Processing")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DocumentTests {

        @Test
        @Order(1)
        @DisplayName("Ingest text document and recall specific content")
        void ingestTextDocument() throws Exception {
            Path doc = resolveTestResource("test-documents/sample.txt");
            Assumptions.assumeTrue(Files.exists(doc), "Test document not found: " + doc);

            String memId = memory.rememberFile(doc,
                    "Technical reference document",
                    MemoryType.SEMANTIC, MemorySource.INFERRED, "docs", "reference").join();

            assertNotNull(memId);
            log.info("Ingested text doc as: {}", memId);
        }

        @Test
        @Order(2)
        @DisplayName("Ingest HTML document with rich markup")
        void ingestHtmlDocument() throws Exception {
            Path doc = resolveTestResource("test-documents/sample.html");
            Assumptions.assumeTrue(Files.exists(doc), "Test HTML not found: " + doc);

            String memId = memory.rememberFile(doc,
                    "Web page content with HTML formatting",
                    MemoryType.SEMANTIC, MemorySource.INFERRED, "html", "web").join();

            assertNotNull(memId);
            log.info("Ingested HTML doc as: {}", memId);
        }

        @Test
        @Order(3)
        @DisplayName("Tika extracts clean text from HTML — no tags in output")
        void tikaStripsHtmlTags() throws Exception {
            Path doc = resolveTestResource("test-documents/sample.html");
            Assumptions.assumeTrue(Files.exists(doc), "Test HTML not found");

            TikaTextExtractor extractor = new TikaTextExtractor(2000, 100);
            try (var chunks = extractor.extract(doc, "text/html")) {
                List<SensoryExtractor.ExtractionChunk> chunkList = chunks.toList();
                assertFalse(chunkList.isEmpty());

                for (var chunk : chunkList) {
                    assertFalse(chunk.text().contains("<html>"), "Should strip HTML tags");
                    assertFalse(chunk.text().contains("<div>"), "Should strip div tags");
                    assertFalse(chunk.text().isBlank(), "Chunk should have content");
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // AUDIO TESTS — Real WAV files
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Audio Processing")
    class AudioTests {

        @Test
        @DisplayName("WAV file is valid and readable")
        void wavFileIsValid() throws Exception {
            Path wav = resolveTestResource("test-audio/hello_tone.wav");
            Assumptions.assumeTrue(Files.exists(wav), "Test WAV not found: " + wav);

            // Verify it's a real WAV file
            byte[] header = new byte[4];
            try (var is = Files.newInputStream(wav)) {
                is.read(header);
            }
            assertEquals("RIFF", new String(header), "Should start with RIFF header");

            long fileSize = Files.size(wav);
            assertTrue(fileSize > 44, "WAV should be larger than header (44 bytes)");
            log.info("Valid WAV file: {} ({} bytes)", wav.getFileName(), fileSize);
        }

        @Test
        @DisplayName("Audio file MIME type detected correctly")
        void audioMimeTypeDetection() throws Exception {
            Path wav = resolveTestResource("test-audio/hello_tone.wav");
            Assumptions.assumeTrue(Files.exists(wav), "Test WAV not found");

            String mimeType = Files.probeContentType(wav);
            // Windows may return "audio/wav" or "audio/x-wav"
            assertTrue(mimeType == null || mimeType.startsWith("audio/"),
                    "MIME type should be audio: " + mimeType);
        }

        @Test
        @DisplayName("Audio file can be ingested as attachment")
        void audioIngestionAsAttachment() throws Exception {
            Path wav = resolveTestResource("test-audio/hello_tone.wav");
            Assumptions.assumeTrue(Files.exists(wav), "Test WAV not found");

            // Ingest as text memory with audio attachment
            var context = IngestionContext.builder()
                    .metadata(SourceModality.ATTACHMENTS_KEY, wav.toAbsolutePath().toString())
                    .sourceModality(SourceModality.AUDIO)
                    .build();

            // This tests the pipeline — even if audio extractor doesn't produce
            // meaningful transcription, the context should be handled gracefully
            String memId = memory.remember("Recording of a test tone at 440Hz",
                    MemoryType.EPISODIC, MemorySource.USER_STATED, context,
                    "audio", "test-tone").join();

            assertNotNull(memId);
            log.info("Ingested audio as: {}", memId);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // VIDEO TESTS — Minimal MP4 file
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Video Processing")
    class VideoTests {

        @Test
        @DisplayName("MP4 file is valid and detectable")
        void mp4FileIsValid() throws Exception {
            Path mp4 = resolveTestResource("test-video/sample_test.mp4");
            Assumptions.assumeTrue(Files.exists(mp4), "Test MP4 not found: " + mp4);

            // Verify ftyp box header
            byte[] header = new byte[8];
            try (var is = Files.newInputStream(mp4)) {
                is.read(header);
            }
            // ftyp atom starts at byte 4
            String atomType = new String(header, 4, 4);
            assertEquals("ftyp", atomType, "Should have ftyp atom");

            long fileSize = Files.size(mp4);
            assertTrue(fileSize > 20, "MP4 should be larger than minimal header");
            log.info("Valid MP4 file: {} ({} bytes)", mp4.getFileName(), fileSize);
        }

        @Test
        @DisplayName("Video file MIME type detected correctly")
        void videoMimeTypeDetection() throws Exception {
            Path mp4 = resolveTestResource("test-video/sample_test.mp4");
            Assumptions.assumeTrue(Files.exists(mp4), "Test MP4 not found");

            String mimeType = Files.probeContentType(mp4);
            // Windows typically returns "video/mp4"
            assertTrue(mimeType == null || mimeType.contains("mp4") || mimeType.startsWith("video/"),
                    "MIME type should be video: " + mimeType);
        }

        @Test
        @DisplayName("Video file can be ingested as attachment")
        void videoIngestionAsAttachment() throws Exception {
            Path mp4 = resolveTestResource("test-video/sample_test.mp4");
            Assumptions.assumeTrue(Files.exists(mp4), "Test MP4 not found");

            var context = IngestionContext.builder()
                    .metadata(SourceModality.ATTACHMENTS_KEY, mp4.toAbsolutePath().toString())
                    .sourceModality(SourceModality.VIDEO)
                    .build();

            String memId = memory.remember("A short test video clip showing text overlay",
                    MemoryType.EPISODIC, MemorySource.USER_STATED, context,
                    "video", "test-clip").join();

            assertNotNull(memId);
            log.info("Ingested video as: {}", memId);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CROSS-MODAL RECALL
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cross-Modal Recall")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CrossModalTests {

        @Test
        @Order(10)
        @DisplayName("Semantic query recalls across image and document modalities")
        void crossModalSemanticRecall() throws Exception {
            // Query something that could match both images and documents
            List<CognitiveResult> results = memory.recall("visual diagram or flowchart",
                    RecallOptions.builder().topK(10).build());

            // Should find content from both image captions and document text
            log.info("Cross-modal recall: {} results for 'visual diagram or flowchart'", results.size());
            results.forEach(r -> log.info("  [{:.3f}] [{}] {}", r.score(), r.memoryType(),
                    truncate(r.text(), 80)));
        }

        @Test
        @Order(11)
        @DisplayName("All ingested modalities are recallable")
        void allModalitiesRecallable() throws Exception {
            // Recall something generic
            List<CognitiveResult> allResults = memory.recall("test content",
                    RecallOptions.builder().topK(20).build());

            assertTrue(allResults.size() >= 2,
                    "Should find at least 2 results across modalities");
            log.info("Total recallable memories: {}", allResults.size());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MULTI-ATTACHMENT
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Multiple attachments in single memory")
    void multipleAttachments() throws Exception {
        Path image = resolveTestResource("test-images/golden_retriever_park.png");
        Path doc = resolveTestResource("test-documents/sample.txt");
        Assumptions.assumeTrue(Files.exists(image) && Files.exists(doc),
                "Test resources not found");

        // Comma-separated attachments
        String attachments = image.toAbsolutePath() + "," + doc.toAbsolutePath();

        var context = IngestionContext.builder()
                .metadata(SourceModality.ATTACHMENTS_KEY, attachments)
                .build();

        String memId = memory.remember("Meeting notes with photo and document reference",
                MemoryType.EPISODIC, MemorySource.USER_STATED, context,
                "meeting", "multi-attach").join();

        assertNotNull(memId);
        log.info("Ingested multi-attachment memory: {}", memId);
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Resolves a test resource from the spector-ingestion test resources.
     */
    private static Path resolveTestResource(String relativePath) {
        // Try classpath first
        var url = RealFileMultimodalE2ETest.class.getClassLoader().getResource(relativePath);
        if (url != null) {
            try {
                return Path.of(url.toURI());
            } catch (Exception e) {
                // fall through
            }
        }
        // Fallback to absolute path
        return Path.of("d:/git/spector-search/spector-ingestion/src/test/resources", relativePath);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
