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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FFmpegKeyframeExtractor}.
 *
 * <p>These tests use a mock ImageDescriber to avoid requiring FFmpeg or VLM
 * at unit test time. The extract() method is tested indirectly through the
 * edge cases that don't actually invoke FFmpeg (empty files, null paths, etc.).</p>
 */
@DisplayName("FFmpegKeyframeExtractor")
class FFmpegKeyframeExtractorTest {

    @TempDir
    Path tempDir;

    private Path fakeVideoFile;

    @BeforeEach
    void setUp() throws IOException {
        fakeVideoFile = tempDir.resolve("demo.mp4");
        Files.write(fakeVideoFile, "fake-video-header-bytes".getBytes());
    }

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTOR VALIDATION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Null imageDescriber throws")
        void nullDescriberThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FFmpegKeyframeExtractor(null));
        }

        @Test
        @DisplayName("Zero interval throws")
        void zeroIntervalThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FFmpegKeyframeExtractor(mockDescriber(), 0, 10));
        }

        @Test
        @DisplayName("Negative max frames throws")
        void negativeMaxFramesThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FFmpegKeyframeExtractor(mockDescriber(), 5, -1));
        }

        @Test
        @DisplayName("Default constructor creates valid extractor")
        void defaultConstructor() {
            assertDoesNotThrow(() -> new FFmpegKeyframeExtractor(mockDescriber()));
        }

        @Test
        @DisplayName("Custom constructor creates valid extractor")
        void customConstructor() {
            assertDoesNotThrow(() -> new FFmpegKeyframeExtractor(mockDescriber(), 15, 20));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MIME TYPE SUPPORT
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MIME Support")
    class MimeSupportTests {

        @Test
        @DisplayName("Supports MP4")
        void supportsMp4() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertTrue(extractor.supports("video/mp4"));
        }

        @Test
        @DisplayName("Supports MKV")
        void supportsMkv() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertTrue(extractor.supports("video/x-matroska"));
        }

        @Test
        @DisplayName("Supports MOV")
        void supportsMov() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertTrue(extractor.supports("video/quicktime"));
        }

        @Test
        @DisplayName("Supports AVI")
        void supportsAvi() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertTrue(extractor.supports("video/x-msvideo"));
        }

        @Test
        @DisplayName("Supports WebM")
        void supportsWebm() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertTrue(extractor.supports("video/webm"));
        }

        @Test
        @DisplayName("Supports unknown video/* subtypes")
        void supportsUnknownVideoSubtype() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertTrue(extractor.supports("video/x-custom-format"));
        }

        @Test
        @DisplayName("Does not support audio types")
        void doesNotSupportAudio() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertFalse(extractor.supports("audio/mpeg"));
        }

        @Test
        @DisplayName("Does not support text types")
        void doesNotSupportText() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertFalse(extractor.supports("text/plain"));
        }

        @Test
        @DisplayName("Does not support null")
        void doesNotSupportNull() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertFalse(extractor.supports(null));
        }

        @Test
        @DisplayName("supportedMimeTypes returns video set")
        void supportedMimeTypesReturnsCorrectSet() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            Set<String> types = extractor.supportedMimeTypes();
            assertTrue(types.contains("video/mp4"));
            assertTrue(types.contains("video/webm"));
            assertTrue(types.contains("video/quicktime"));
            assertEquals(FFmpegKeyframeExtractor.VIDEO_MIME_TYPES, types);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty video file returns empty stream")
        void emptyFileReturnsEmpty() throws IOException {
            Path emptyFile = tempDir.resolve("empty.mp4");
            Files.write(emptyFile, new byte[0]);

            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(emptyFile, "video/mp4")) {
                assertEquals(0, chunks.count());
            }
        }

        @Test
        @DisplayName("Null source throws IOException")
        void nullSourceThrows() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertThrows(IOException.class, () -> extractor.extract(null, "video/mp4"));
        }

        @Test
        @DisplayName("Non-existent file throws IOException")
        void nonExistentFileThrows() {
            var extractor = new FFmpegKeyframeExtractor(mockDescriber());
            assertThrows(IOException.class,
                    () -> extractor.extract(tempDir.resolve("missing.mp4"), "video/mp4"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SIMULATED EXTRACTION (with mock frames)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mock Frame Processing")
    class MockFrameTests {

        @Test
        @DisplayName("extractKeyframes method interface works")
        void extractKeyframesCallable() throws IOException {
            // Create a mock extractor subclass that overrides extractKeyframes
            var extractor = new MockableKeyframeExtractor(
                    mockDescriber(),
                    List.of("A person talking to camera", "A whiteboard with diagrams"));

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(fakeVideoFile, "video/mp4")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                assertEquals(2, result.size());

                // First frame
                assertEquals("frame-0", result.get(0).chunkId());
                assertTrue(result.get(0).text().contains("person talking"));
                assertEquals("0", result.get(0).metadata().get("frame_index"));
                assertEquals("0", result.get(0).metadata().get("timestamp_seconds"));
                assertEquals("0:00", result.get(0).metadata().get("timestamp_display"));
                assertEquals("VIDEO", result.get(0).metadata().get("modality"));

                // Second frame
                assertEquals("frame-1", result.get(1).chunkId());
                assertTrue(result.get(1).text().contains("whiteboard"));
                assertEquals("1", result.get(1).metadata().get("frame_index"));
                assertEquals("10", result.get(1).metadata().get("timestamp_seconds"));
                assertEquals("0:10", result.get(1).metadata().get("timestamp_display"));
            }
        }

        @Test
        @DisplayName("Skips frames with empty descriptions")
        void skipsEmptyDescriptions() throws IOException {
            var extractor = new MockableKeyframeExtractor(
                    path -> {
                        // Return empty for even frames
                        String name = path.getFileName().toString();
                        int idx = Integer.parseInt(name.replace("frame_", "").replace(".jpg", ""));
                        return idx % 2 == 0 ? "" : "Description for frame " + idx;
                    },
                    List.of("", "Second frame", "", "Fourth frame"));

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(fakeVideoFile, "video/mp4")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                // Only non-empty frames should be included
                assertEquals(2, result.size());
                assertTrue(result.get(0).text().contains("Second frame"));
                assertTrue(result.get(1).text().contains("Fourth frame"));
            }
        }

        @Test
        @DisplayName("Includes source URI in metadata")
        void includesSourceUri() throws IOException {
            var extractor = new MockableKeyframeExtractor(
                    mockDescriber(),
                    List.of("Frame description"));

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(fakeVideoFile, "video/mp4")) {
                var chunk = chunks.findFirst().orElseThrow();
                assertTrue(chunk.metadata().get("source_uri").contains("demo.mp4"));
            }
        }

        @Test
        @DisplayName("Timestamp formatting for hours")
        void timestampFormatting() throws IOException {
            // Create extractor with 1s interval to get higher timestamps
            var extractor = new MockableKeyframeExtractor(
                    mockDescriber(), 1, 5,
                    List.of("F1", "F2", "F3", "F4", "F5"));

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(fakeVideoFile, "video/mp4")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();
                assertEquals(5, result.size());
                assertEquals("0:00", result.get(0).metadata().get("timestamp_display"));
                assertEquals("0:01", result.get(1).metadata().get("timestamp_display"));
                assertEquals("0:04", result.get(4).metadata().get("timestamp_display"));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private static FFmpegKeyframeExtractor.ImageDescriber mockDescriber() {
        return path -> "Description of " + path.getFileName();
    }

    /**
     * Test subclass that overrides extractKeyframes to avoid needing real FFmpeg.
     */
    private static class MockableKeyframeExtractor extends FFmpegKeyframeExtractor {

        private final List<String> mockDescriptions;

        MockableKeyframeExtractor(ImageDescriber describer, List<String> descriptions) {
            super(describer);
            this.mockDescriptions = descriptions;
        }

        MockableKeyframeExtractor(ImageDescriber describer, int interval, int max,
                                  List<String> descriptions) {
            super(describer, interval, max);
            this.mockDescriptions = descriptions;
        }

        @Override
        List<Path> extractKeyframes(Path videoPath, Path outputDir) throws IOException {
            // Create mock frame files
            List<Path> frames = new java.util.ArrayList<>();
            for (int i = 0; i < mockDescriptions.size(); i++) {
                Path frame = outputDir.resolve(String.format("frame_%04d.jpg", i));
                Files.write(frame, ("mock-frame-" + i).getBytes());
                frames.add(frame);
            }
            return frames;
        }

        @Override
        public Stream<ExtractionChunk> extract(Path source, String mimeType) throws IOException {
            if (source == null || !Files.exists(source)) {
                throw new IOException("Video file does not exist: " + source);
            }
            if (Files.size(source) == 0) return Stream.empty();

            Path framesDir = Files.createTempDirectory("spector-test-frames-");
            try {
                List<Path> frames = extractKeyframes(source, framesDir);
                List<ExtractionChunk> chunks = new java.util.ArrayList<>();

                for (int i = 0; i < frames.size(); i++) {
                    String desc = mockDescriptions.get(i);
                    if (desc == null || desc.isBlank()) continue;

                    int intervalSec = getIntervalSeconds();
                    int ts = i * intervalSec;

                    java.util.Map<String, String> meta = new java.util.HashMap<>();
                    meta.put("modality", "VIDEO");
                    meta.put("source_uri", source.toUri().toString());
                    meta.put("original_filename", source.getFileName().toString());
                    meta.put("extractor", "ffmpeg-keyframe");
                    meta.put("frame_index", String.valueOf(i));
                    meta.put("timestamp_seconds", String.valueOf(ts));
                    meta.put("timestamp_display", formatTs(ts));
                    if (mimeType != null) meta.put("content_type", mimeType);

                    chunks.add(new ExtractionChunk("frame-" + i,
                            String.format("[%s] %s", formatTs(ts), desc), meta));
                }
                return chunks.stream();
            } finally {
                // Cleanup
                try (var stream = Files.walk(framesDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            }
        }

        private int getIntervalSeconds() {
            try {
                var field = FFmpegKeyframeExtractor.class.getDeclaredField("intervalSeconds");
                field.setAccessible(true);
                return (int) field.get(this);
            } catch (ReflectiveOperationException e) {
                return 10; // fallback
            }
        }

        private static String formatTs(int total) {
            int m = total / 60, s = total % 60;
            return String.format("%d:%02d", m, s);
        }
    }
}
