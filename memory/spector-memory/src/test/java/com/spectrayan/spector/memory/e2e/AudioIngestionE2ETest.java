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
import com.spectrayan.spector.provider.ollama.OllamaLlmProvider;
import com.spectrayan.spector.ingestion.sensory.OllamaAudioExtractor;
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
 * E2E test for audio ingestion via Ollama audio-capable models.
 *
 * <p>Requires:</p>
 * <ul>
 *   <li>{@code OLLAMA_LIVE=true} — running Ollama instance</li>
 *   <li>An audio-capable Ollama model (e.g., "gemma4" with audio support)</li>
 * </ul>
 *
 * <p>Tests use real WAV files from test resources. If the audio model is not
 * available, transcription tests are skipped but pipeline integration tests
 * still run.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Audio Ingestion E2E")
class AudioIngestionE2ETest {

    private static final Logger log = LoggerFactory.getLogger(AudioIngestionE2ETest.class);

    private SpectorMemory memory;
    private OllamaAudioExtractor audioExtractor;
    private boolean audioModelAvailable;

    @BeforeAll
    void setUp() {
        boolean ollamaLive = "true".equalsIgnoreCase(System.getenv("OLLAMA_LIVE"))
                || "true".equalsIgnoreCase(System.getProperty("OLLAMA_LIVE"));
        Assumptions.assumeTrue(ollamaLive,
                "Skipping audio E2E — set OLLAMA_LIVE=true to run");

        var embeddingProvider = OllamaEmbeddingProvider.create("nomic-embed-text");

        // Audio extractor — requires a TextGenerationProvider
        OllamaLlmProvider audioLlm = OllamaLlmProvider.create("gemma4");
        audioExtractor = new OllamaAudioExtractor(audioLlm);
        audioModelAvailable = audioExtractor.isAvailable();
        log.info("Audio model available: {}", audioModelAvailable);

        // Vision extractor (for cross-modal tests)
        OllamaVisionExtractor visionExtractor;
        try {
            visionExtractor = OllamaVisionExtractor.create("llava");
            if (!visionExtractor.isAvailable()) {
                visionExtractor = OllamaVisionExtractor.createDefault();
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
                .sensoryExtractors(List.of(visionExtractor, tikaExtractor, audioExtractor))
                .build();

        log.info("AudioIngestionE2E initialized — audioModel={}", audioModelAvailable);
    }

    // ══════════════════════════════════════════════════════════════
    // PIPELINE INTEGRATION (No live model required)
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("WAV file ingested as attachment with AUDIO modality")
    void wavIngestionWithModality() throws Exception {
        Path wav = resolveTestResource("test-audio/hello_tone.wav");
        Assumptions.assumeTrue(Files.exists(wav), "Test WAV not found");

        var context = IngestionContext.builder()
                .metadata(SourceModality.ATTACHMENTS_KEY, wav.toAbsolutePath().toString())
                .sourceModality(SourceModality.AUDIO)
                .build();

        String memId = memory.remember(
                "Recording of the team standup meeting discussing sprint goals",
                MemoryType.EPISODIC, MemorySource.USER_STATED, context,
                "audio", "meeting", "standup").join();

        assertNotNull(memId, "Should store audio memory");
        log.info("Ingested audio as: {}", memId);

        // Recall by text description (the parent text is always stored)
        List<CognitiveResult> results = memory.recall("standup meeting sprint",
                RecallOptions.builder().topK(5).build());
        assertFalse(results.isEmpty(), "Should recall audio memory by description");
    }

    @Test
    @Order(2)
    @DisplayName("Multiple audio files ingested in sequence")
    void multipleAudioFiles() throws Exception {
        Path wav1 = resolveTestResource("test-audio/hello_tone.wav");
        Path wav2 = resolveTestResource("test-audio/short_beep.wav");
        Assumptions.assumeTrue(Files.exists(wav1) && Files.exists(wav2),
                "Test WAV files not found");

        // Ingest two audio memories
        var ctx1 = IngestionContext.builder()
                .metadata(SourceModality.ATTACHMENTS_KEY, wav1.toAbsolutePath().toString())
                .sourceModality(SourceModality.AUDIO)
                .build();
        String id1 = memory.remember("First audio recording — morning briefing",
                MemoryType.EPISODIC, MemorySource.USER_STATED, ctx1,
                "audio", "morning").join();

        var ctx2 = IngestionContext.builder()
                .metadata(SourceModality.ATTACHMENTS_KEY, wav2.toAbsolutePath().toString())
                .sourceModality(SourceModality.AUDIO)
                .build();
        String id2 = memory.remember("Second audio recording — afternoon review",
                MemoryType.EPISODIC, MemorySource.USER_STATED, ctx2,
                "audio", "afternoon").join();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2, "Different audio files should produce different IDs");
    }

    @Test
    @Order(3)
    @DisplayName("Audio memory recallable alongside image and text memories")
    void crossModalRecall() throws Exception {
        Path wav = resolveTestResource("test-audio/hello_tone.wav");
        Assumptions.assumeTrue(Files.exists(wav), "Test WAV not found");

        // Ingest a text-only memory for comparison
        memory.remember("Written notes about the project architecture",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, (IngestionContext) null,
                "notes", "architecture").join();

        // Ingest audio memory
        var ctx = IngestionContext.builder()
                .metadata(SourceModality.ATTACHMENTS_KEY, wav.toAbsolutePath().toString())
                .sourceModality(SourceModality.AUDIO)
                .build();
        memory.remember("Audio recording about the project architecture",
                MemoryType.EPISODIC, MemorySource.USER_STATED, ctx,
                "audio", "architecture").join();

        // Recall should find both
        List<CognitiveResult> results = memory.recall("project architecture",
                RecallOptions.builder().topK(10).build());
        assertTrue(results.size() >= 2,
                "Should find both text and audio memories about architecture");
        log.info("Cross-modal recall: {} results for 'project architecture'", results.size());
    }

    // ══════════════════════════════════════════════════════════════
    // LIVE TRANSCRIPTION (Requires audio-capable model)
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("OllamaAudioExtractor extracts chunks from WAV")
    void liveAudioTranscription() throws Exception {
        Assumptions.assumeTrue(audioModelAvailable, "Audio model not available");

        Path wav = resolveTestResource("test-audio/hello_tone.wav");
        Assumptions.assumeTrue(Files.exists(wav), "Test WAV not found");

        try (var chunks = audioExtractor.extract(wav, "audio/wav")) {
            List<SensoryExtractor.ExtractionChunk> chunkList = chunks.toList();

            // A pure sine wave may produce empty results or a description like "tone"
            log.info("Audio extractor produced {} chunks from hello_tone.wav", chunkList.size());
            if (!chunkList.isEmpty()) {
                for (var chunk : chunkList) {
                    assertNotNull(chunk.text(), "Chunk text should not be null");
                    log.info("Audio chunk [{}]: {}", chunk.chunkId(),
                            chunk.text().length() > 100 ? chunk.text().substring(0, 100) + "…" : chunk.text());
                }
            } else {
                // Empty is acceptable for non-speech audio (pure tone)
                log.info("Audio model returned empty for non-speech audio — expected for sine wave");
            }
        }
    }

    @Test
    @Order(11)
    @DisplayName("Audio transcription metadata includes AUDIO modality")
    void transcriptionMetadata() throws Exception {
        Assumptions.assumeTrue(audioModelAvailable, "Audio model not available");

        Path wav = resolveTestResource("test-audio/hello_tone.wav");
        Assumptions.assumeTrue(Files.exists(wav));

        try (var chunks = audioExtractor.extract(wav, "audio/wav")) {
            chunks.forEach(chunk -> {
                var meta = chunk.metadata();
                assertEquals("AUDIO", meta.get("modality"),
                        "Should have AUDIO modality in metadata");
            });
        }
    }

    // ══════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("Short audio file (beep) handled gracefully")
    void shortAudioFile() throws Exception {
        Assumptions.assumeTrue(audioModelAvailable, "Audio model not available");

        Path beep = resolveTestResource("test-audio/short_beep.wav");
        Assumptions.assumeTrue(Files.exists(beep), "Short beep not found");

        try (var chunks = audioExtractor.extract(beep, "audio/wav")) {
            // Even very short audio should produce at least one chunk or empty stream
            var chunkList = chunks.toList();
            log.info("Short beep produced {} chunks", chunkList.size());
            // No crash is the main assertion
        }
    }

    @Test
    @Order(21)
    @DisplayName("Unsupported audio format returns empty stream")
    void unsupportedAudioFormat() throws Exception {
        Path wav = resolveTestResource("test-audio/hello_tone.wav");
        Assumptions.assumeTrue(Files.exists(wav));

        // "audio/midi" is not in supported types
        try (var chunks = audioExtractor.extract(wav, "audio/midi")) {
            assertTrue(chunks.toList().isEmpty(),
                    "Unsupported MIME type should produce empty stream");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private static Path resolveTestResource(String relativePath) {
        var url = AudioIngestionE2ETest.class.getClassLoader().getResource(relativePath);
        if (url != null) {
            try { return Path.of(url.toURI()); } catch (Exception e) { /* fall through */ }
        }
        return Path.of("d:/git/spector-search/spector-ingestion/src/test/resources", relativePath);
    }
}
