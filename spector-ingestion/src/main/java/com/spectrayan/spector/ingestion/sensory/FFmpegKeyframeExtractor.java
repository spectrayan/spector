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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extracts keyframes from video files using FFmpeg, then describes each frame
 * via a Vision Language Model (VLM) for cognitive memory storage.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>FFmpeg extracts keyframes (I-frames) at configurable intervals</li>
 *   <li>Each keyframe image is sent to the VLM for captioning</li>
 *   <li>Captions become ExtractionChunks with temporal metadata</li>
 * </ol>
 *
 * <h3>Requirements</h3>
 * <ul>
 *   <li>FFmpeg must be installed and on PATH</li>
 *   <li>A VLM provider (e.g., OllamaImageDescriber) for frame captioning</li>
 * </ul>
 *
 * <h3>Supported Formats</h3>
 * <p>Any format FFmpeg can decode: MP4, AVI, MKV, MOV, WebM, etc.</p>
 *
 * @see SensoryExtractor
 */
public class FFmpegKeyframeExtractor implements SensoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(FFmpegKeyframeExtractor.class);

    /** Standard video MIME types. */
    public static final Set<String> VIDEO_MIME_TYPES = Set.of(
            "video/mp4",
            "video/x-matroska",   // MKV
            "video/quicktime",    // MOV
            "video/x-msvideo",    // AVI
            "video/webm",
            "video/mpeg",
            "video/3gpp",
            "video/x-flv"
    );

    /** Default keyframe extraction interval in seconds. */
    private static final int DEFAULT_INTERVAL_SECONDS = 10;

    /** Default max keyframes to extract. */
    private static final int DEFAULT_MAX_KEYFRAMES = 30;

    private final int intervalSeconds;
    private final int maxKeyframes;
    private final ImageDescriber imageDescriber;

    /**
     * Functional interface for image description (VLM bridge).
     *
     * <p>Decouples video extraction from the specific VLM implementation,
     * allowing use of Ollama, OpenAI Vision, or any other image captioning service.</p>
     */
    @FunctionalInterface
    public interface ImageDescriber {
        /**
         * Describes an image file, returning a text caption.
         *
         * @param imagePath path to the image file
         * @return text description of the image
         * @throws IOException if description fails
         */
        String describe(Path imagePath) throws IOException;
    }

    /**
     * Creates a keyframe extractor with default settings (10s interval, max 30 frames).
     *
     * @param imageDescriber VLM bridge for captioning extracted frames
     */
    public FFmpegKeyframeExtractor(ImageDescriber imageDescriber) {
        this(imageDescriber, DEFAULT_INTERVAL_SECONDS, DEFAULT_MAX_KEYFRAMES);
    }

    /**
     * Creates a keyframe extractor with custom settings.
     *
     * @param imageDescriber  VLM bridge for captioning extracted frames
     * @param intervalSeconds seconds between keyframe captures
     * @param maxKeyframes    maximum number of keyframes to extract
     */
    public FFmpegKeyframeExtractor(ImageDescriber imageDescriber, int intervalSeconds, int maxKeyframes) {
        if (imageDescriber == null) throw new IllegalArgumentException("imageDescriber is required");
        if (intervalSeconds <= 0) throw new IllegalArgumentException("intervalSeconds must be > 0");
        if (maxKeyframes <= 0) throw new IllegalArgumentException("maxKeyframes must be > 0");
        this.imageDescriber = imageDescriber;
        this.intervalSeconds = intervalSeconds;
        this.maxKeyframes = maxKeyframes;
        log.info("FFmpegKeyframeExtractor: interval={}s, maxFrames={}", intervalSeconds, maxKeyframes);
    }

    @Override
    public Stream<ExtractionChunk> extract(Path source, String mimeType) throws IOException {
        if (source == null || !Files.exists(source)) {
            throw new IOException("Video file does not exist: " + source);
        }

        long fileSize = Files.size(source);
        if (fileSize == 0) {
            log.debug("Skipping empty video file: {}", source.getFileName());
            return Stream.empty();
        }

        log.info("Extracting keyframes from: {} ({}B)", source.getFileName(), fileSize);

        // Create temp directory for extracted frames
        Path framesDir = Files.createTempDirectory("spector-frames-");

        try {
            // Extract keyframes via FFmpeg
            List<Path> frames = extractKeyframes(source, framesDir);
            if (frames.isEmpty()) {
                log.warn("No keyframes extracted from: {}", source.getFileName());
                return Stream.empty();
            }

            log.info("Extracted {} keyframes from {}", frames.size(), source.getFileName());

            // Describe each frame via VLM and build chunks
            List<ExtractionChunk> chunks = new ArrayList<>();
            for (int i = 0; i < frames.size(); i++) {
                try {
                    Path frame = frames.get(i);
                    String description = imageDescriber.describe(frame);

                    if (description == null || description.isBlank()) {
                        log.debug("Empty description for frame {}", i);
                        continue;
                    }

                    int timestampSeconds = i * intervalSeconds;

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("modality", "VIDEO");
                    metadata.put("source_uri", source.toUri().toString());
                    metadata.put("original_filename", source.getFileName().toString());
                    metadata.put("extractor", "ffmpeg-keyframe");
                    metadata.put("frame_index", String.valueOf(i));
                    metadata.put("timestamp_seconds", String.valueOf(timestampSeconds));
                    metadata.put("timestamp_display", formatTimestamp(timestampSeconds));
                    if (mimeType != null) metadata.put("content_type", mimeType);

                    // Prefix description with temporal context
                    String text = String.format("[%s] %s", formatTimestamp(timestampSeconds), description);

                    chunks.add(new ExtractionChunk("frame-" + i, text, metadata));
                } catch (IOException e) {
                    log.warn("Failed to describe frame {}: {}", i, e.getMessage());
                }
            }

            return chunks.stream();
        } finally {
            // Cleanup temp frames
            cleanupFrames(framesDir);
        }
    }

    /**
     * Extracts keyframes from a video file using FFmpeg.
     *
     * @param videoPath path to the video file
     * @param outputDir directory to write extracted frames
     * @return list of extracted frame image paths
     * @throws IOException if FFmpeg execution fails
     */
    List<Path> extractKeyframes(Path videoPath, Path outputDir) throws IOException {
        // FFmpeg command: extract 1 frame every N seconds as JPEG
        String outputPattern = outputDir.resolve("frame_%04d.jpg").toString();
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", videoPath.toString(),
                "-vf", "fps=1/" + intervalSeconds,
                "-frames:v", String.valueOf(maxKeyframes),
                "-q:v", "2",  // High quality JPEG
                outputPattern
        );
        pb.redirectErrorStream(true);

        log.debug("Running FFmpeg: {}", pb.command());

        Process process = pb.start();
        // Read output to prevent blocking
        byte[] output = process.getInputStream().readAllBytes();

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg was interrupted", e);
        }

        if (exitCode != 0) {
            String errorOutput = new String(output);
            throw new IOException("FFmpeg failed (exit " + exitCode + "): " +
                    errorOutput.substring(0, Math.min(errorOutput.length(), 500)));
        }

        // Collect extracted frames
        List<Path> frames = new ArrayList<>();
        try (var stream = Files.list(outputDir)) {
            stream.filter(p -> p.toString().endsWith(".jpg"))
                    .sorted()
                    .forEach(frames::add);
        }

        return frames;
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return VIDEO_MIME_TYPES;
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        return VIDEO_MIME_TYPES.contains(mimeType.toLowerCase())
                || mimeType.toLowerCase().startsWith("video/");
    }

    @Override
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static String formatTimestamp(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    private static void cleanupFrames(Path framesDir) {
        try {
            if (Files.exists(framesDir)) {
                try (var stream = Files.walk(framesDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                            });
                }
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp frames: {}", e.getMessage());
        }
    }
}
