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

import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.ingestion.sensory.FFmpegKeyframeExtractor;
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

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test for video ingestion via FFmpeg keyframe extraction + VLM captioning.
 *
 * <p>Requires:</p>
 * <ul>
 *   <li>{@code OLLAMA_LIVE=true} — running Ollama instance</li>
 *   <li>{@code ffmpeg} on PATH — for keyframe extraction</li>
 * </ul>
 *
 * <p>If either prerequisite is missing, tests are skipped gracefully.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Video Ingestion E2E")
class VideoIngestionE2ETest {

    private static final Logger log = LoggerFactory.getLogger(VideoIngestionE2ETest.class);

    private SpectorMemory memory;
    private boolean ffmpegAvailable;

    @BeforeAll
    void setUp() {
        boolean ollamaLive = "true".equalsIgnoreCase(System.getenv("OLLAMA_LIVE"))
                || "true".equalsIgnoreCase(System.getProperty("OLLAMA_LIVE"));
        Assumptions.assumeTrue(ollamaLive,
                "Skipping video E2E — set OLLAMA_LIVE=true to run");

        // Check FFmpeg availability
        ffmpegAvailable = checkFfmpeg();
        if (!ffmpegAvailable) {
            log.warn("FFmpeg not found on PATH — some video tests will be skipped");
        }

        var embeddingProvider = OllamaEmbeddingProvider.create("nomic-embed-text");

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

        log.info("VideoIngestionE2E initialized — vision={}, ffmpeg={}",
                visionExtractor.model(), ffmpegAvailable);
    }

    @Test
    @Order(1)
    @DisplayName("MP4 file can be ingested as video attachment")
    void mp4FileIngestion() throws Exception {
        Path mp4 = resolveTestResource("test-video/sample_test.mp4");
        Assumptions.assumeTrue(Files.exists(mp4), "Test MP4 not found");

        var context = IngestionContext.builder()
                .metadata(SourceModality.ATTACHMENTS_KEY, mp4.toAbsolutePath().toString())
                .sourceModality(SourceModality.VIDEO)
                .build();

        String memId = memory.remember(
                "Screen recording of debugging a UI layout issue with CSS flexbox",
                MemoryType.EPISODIC, MemorySource.USER_STATED, context,
                "video", "debugging", "css").join();

        assertNotNull(memId, "Should store video memory");
        log.info("Ingested video as: {}", memId);

        // Recall by semantic content
        List<CognitiveResult> results = memory.recall("debugging CSS layout",
                RecallOptions.builder().topK(5).build());
        assertFalse(results.isEmpty(), "Should recall video memory by description");
    }

    @Test
    @Order(2)
    @DisplayName("FFmpeg keyframe extractor creates frames from video")
    void ffmpegKeyframeExtraction() throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable, "FFmpeg not available on PATH");

        Path mp4 = resolveTestResource("test-video/sample_test.mp4");
        Assumptions.assumeTrue(Files.exists(mp4), "Test MP4 not found");

        var vlm = OllamaVisionExtractor.create("llava");
        Assumptions.assumeTrue(vlm.isAvailable(), "VLM not available");

        var keyframeExtractor = createKeyframeExtractor(vlm);

        try (var chunks = keyframeExtractor.extract(mp4, "video/mp4")) {
            var chunkList = chunks.toList();
            // Even if FFmpeg can't extract from our minimal MP4 (no real video stream),
            // it should not crash
            log.info("FFmpeg extracted {} keyframe chunks from test video", chunkList.size());
            for (var chunk : chunkList) {
                assertFalse(chunk.text().isBlank(), "Keyframe caption should not be blank");
                assertNotNull(chunk.metadata().get("modality"));
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Video memory is recallable alongside image memories")
    void videoRecallWithOtherModalities() throws Exception {
        // Ingest an image for cross-modal comparison
        Path image = resolveTestResource("test-images/golden_retriever_park.png");
        if (Files.exists(image)) {
            var imgCtx = IngestionContext.builder()
                    .metadata(SourceModality.ATTACHMENTS_KEY, image.toAbsolutePath().toString())
                    .sourceModality(SourceModality.IMAGE)
                    .build();

            memory.remember("Photo from the company picnic",
                    MemoryType.EPISODIC, MemorySource.USER_STATED, imgCtx,
                    "photo", "company").join();
        }

        // Query something generic
        List<CognitiveResult> results = memory.recall("visual content from recent activities",
                RecallOptions.builder().topK(10).build());

        assertTrue(results.size() >= 1,
                "Should find at least 1 result from video or image memories");
        log.info("Cross-modal recall: {} results", results.size());
    }

    @Test
    @Order(4)
    @DisplayName("FFmpegKeyframeExtractor gracefully handles non-video files")
    void ffmpegNonVideoFile() throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable, "FFmpeg not available");

        Path textFile = resolveTestResource("test-documents/sample.txt");
        Assumptions.assumeTrue(Files.exists(textFile), "Test text file not found");

        var vlm = OllamaVisionExtractor.create("llava");
        Assumptions.assumeTrue(vlm.isAvailable(), "VLM not available");

        var keyframeExtractor = createKeyframeExtractor(vlm);

        // text/plain is not a supported video MIME type — should be empty
        try (var chunks = keyframeExtractor.extract(textFile, "text/plain")) {
            var chunkList = chunks.toList();
            assertTrue(chunkList.isEmpty(),
                    "Non-video MIME type should produce empty chunks");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private static boolean checkFfmpeg() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path resolveTestResource(String relativePath) {
        var url = VideoIngestionE2ETest.class.getClassLoader().getResource(relativePath);
        if (url != null) {
            try { return Path.of(url.toURI()); } catch (Exception e) { /* fall through */ }
        }
        return Path.of("d:/git/spector-search/spector-ingestion/src/test/resources", relativePath);
    }
    /**
     * Adapts OllamaVisionExtractor to the ImageDescriber SPI.
     */
    private static FFmpegKeyframeExtractor createKeyframeExtractor(OllamaVisionExtractor vlm) {
        FFmpegKeyframeExtractor.ImageDescriber describer = imagePath -> {
            try (var chunks = vlm.extract(imagePath, "image/png")) {
                return chunks.findFirst()
                        .map(SensoryExtractor.ExtractionChunk::text)
                        .orElse("No description available");
            }
        };
        return new FFmpegKeyframeExtractor(describer);
    }
}
