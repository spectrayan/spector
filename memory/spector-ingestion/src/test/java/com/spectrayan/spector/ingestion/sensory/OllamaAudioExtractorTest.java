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
package com.spectrayan.spector.ingestion.sensory;

import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OllamaAudioExtractor} with mock LLM provider.
 */
@DisplayName("OllamaAudioExtractor")
class OllamaAudioExtractorTest {

    @TempDir
    Path tempDir;

    private Path fakeAudioFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create a fake audio file (not real audio â€” just bytes for testing the extraction flow)
        fakeAudioFile = tempDir.resolve("interview.mp3");
        Files.write(fakeAudioFile, "fake-mp3-header-bytes-for-testing".getBytes());
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // SUCCESSFUL TRANSCRIPTION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("Transcription")
    class TranscriptionTests {

        @Test
        @DisplayName("Transcribes audio file and returns chunk")
        void transcribesAudioFile() throws IOException {
            var extractor = new OllamaAudioExtractor(
                    mockLlm("Speaker 1: Hello, welcome to the podcast.\nSpeaker 2: Thanks for having me."));

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(fakeAudioFile, "audio/mpeg")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                assertEquals(1, result.size());
                assertTrue(result.getFirst().text().contains("welcome to the podcast"));
                assertEquals("transcript-0", result.getFirst().chunkId());
            }
        }

        @Test
        @DisplayName("Sets correct metadata")
        void setsCorrectMetadata() throws IOException {
            var extractor = new OllamaAudioExtractor(mockLlm("Test transcription result."));

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(fakeAudioFile, "audio/mpeg")) {
                var chunk = chunks.findFirst().orElseThrow();

                assertEquals("AUDIO", chunk.metadata().get("modality"));
                assertEquals("ollama-audio", chunk.metadata().get("extractor"));
                assertEquals("mock-audio-model", chunk.metadata().get(
                        AudioTranscriptExtractor.TranscriptMetadata.MODEL));
                assertEquals("audio/mpeg", chunk.metadata().get("content_type"));
                assertEquals("interview.mp3", chunk.metadata().get("original_filename"));
                assertNotNull(chunk.metadata().get("source_uri"));
                assertNotNull(chunk.metadata().get("file_size_bytes"));
            }
        }

        @Test
        @DisplayName("Strips whitespace from transcription")
        void stripsWhitespace() throws IOException {
            var extractor = new OllamaAudioExtractor(mockLlm("  \n  Padded transcript.  \n  "));

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(fakeAudioFile, "audio/mpeg")) {
                var chunk = chunks.findFirst().orElseThrow();
                assertEquals("Padded transcript.", chunk.text());
            }
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // MIME TYPE SUPPORT
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("MIME Support")
    class MimeSupportTests {

        @Test
        @DisplayName("Supports MP3")
        void supportsMp3() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertTrue(extractor.supports("audio/mpeg"));
        }

        @Test
        @DisplayName("Supports WAV")
        void supportsWav() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertTrue(extractor.supports("audio/wav"));
            assertTrue(extractor.supports("audio/wave"));
            assertTrue(extractor.supports("audio/x-wav"));
        }

        @Test
        @DisplayName("Supports FLAC")
        void supportsFlac() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertTrue(extractor.supports("audio/flac"));
        }

        @Test
        @DisplayName("Supports OGG")
        void supportsOgg() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertTrue(extractor.supports("audio/ogg"));
        }

        @Test
        @DisplayName("Supports M4A")
        void supportsM4a() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertTrue(extractor.supports("audio/mp4"));
            assertTrue(extractor.supports("audio/x-m4a"));
        }

        @Test
        @DisplayName("Supports WebM Audio")
        void supportsWebm() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertTrue(extractor.supports("audio/webm"));
        }

        @Test
        @DisplayName("Does not support non-audio types")
        void doesNotSupportNonAudio() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertFalse(extractor.supports("image/jpeg"));
            assertFalse(extractor.supports("text/plain"));
            assertFalse(extractor.supports("video/mp4"));
        }

        @Test
        @DisplayName("Does not support null")
        void doesNotSupportNull() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertFalse(extractor.supports(null));
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // EDGE CASES
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty file returns empty stream")
        void emptyFileReturnsEmpty() throws IOException {
            Path emptyFile = tempDir.resolve("empty.mp3");
            Files.write(emptyFile, new byte[0]);

            var extractor = new OllamaAudioExtractor(mockLlm("should not be called"));
            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(emptyFile, "audio/mpeg")) {
                assertEquals(0, chunks.count());
            }
        }

        @Test
        @DisplayName("Null source throws IOException")
        void nullSourceThrows() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertThrows(IOException.class, () -> extractor.extract(null, "audio/mpeg"));
        }

        @Test
        @DisplayName("Non-existent file throws IOException")
        void nonExistentFileThrows() {
            var extractor = new OllamaAudioExtractor(mockLlm("test"));
            assertThrows(IOException.class,
                    () -> extractor.extract(tempDir.resolve("missing.mp3"), "audio/mpeg"));
        }

        @Test
        @DisplayName("Empty transcription returns empty stream")
        void emptyTranscriptionReturnsEmpty() throws IOException {
            var extractor = new OllamaAudioExtractor(mockLlm(""));

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(fakeAudioFile, "audio/mpeg")) {
                assertEquals(0, chunks.count());
            }
        }

        @Test
        @DisplayName("Null transcription returns empty stream")
        void nullTranscriptionReturnsEmpty() throws IOException {
            var extractor = new OllamaAudioExtractor(mockLlm(null));

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(fakeAudioFile, "audio/mpeg")) {
                assertEquals(0, chunks.count());
            }
        }

        @Test
        @DisplayName("LLM generation failure throws IOException")
        void llmFailureThrows() {
            LlmProvider failingLlm = new LlmProvider() {
                @Override
                public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                    throw new GenerationException("Model crashed", new RuntimeException());
                }
                @Override
                public String modelName() { return "failing-model"; }
                @Override
                public boolean isAvailable() { return true; }
            };

            var extractor = new OllamaAudioExtractor(failingLlm);
            assertThrows(IOException.class,
                    () -> extractor.extract(fakeAudioFile, "audio/mpeg"));
        }

        @Test
        @DisplayName("Null LLM provider throws in constructor")
        void nullLlmThrows() {
            assertThrows(IllegalArgumentException.class, () -> new OllamaAudioExtractor(null));
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // AVAILABILITY
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @DisplayName("isAvailable delegates to LLM provider")
    void isAvailableDelegates() {
        var extractor = new OllamaAudioExtractor(mockLlm("test"));
        assertTrue(extractor.isAvailable());
    }

    @Test
    @DisplayName("transcriptionModel returns LLM model name")
    void transcriptionModelReturnsName() {
        var extractor = new OllamaAudioExtractor(mockLlm("test"));
        assertEquals("mock-audio-model", extractor.transcriptionModel());
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // NULL MIME TYPE
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Test
    @DisplayName("Null mime type still works â€” content_type omitted from metadata")
    void nullMimeTypeWorks() throws IOException {
        var extractor = new OllamaAudioExtractor(mockLlm("Transcribed with null mime."));

        try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                     extractor.extract(fakeAudioFile, null)) {
            var chunk = chunks.findFirst().orElseThrow();
            assertNull(chunk.metadata().get("content_type"));
            assertEquals("Transcribed with null mime.", chunk.text());
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // HELPERS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private static LlmProvider mockLlm(String fixedResponse) {
        return new LlmProvider() {
            @Override
            public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                return new LlmResponse(fixedResponse == null ? "" : fixedResponse, 0, 0, "mock-audio-model");
            }
            @Override
            public String generate(String prompt) {
                return fixedResponse;
            }
            @Override
            public String modelName() {
                return "mock-audio-model";
            }
            @Override
            public boolean isAvailable() {
                return true;
            }
        };
    }
}
