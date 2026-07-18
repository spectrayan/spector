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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * SPI for transcribing audio files into text chunks.
 *
 * <p>Implementations transform audio content (MP3, WAV, FLAC, etc.) into
 * text transcripts that can be embedded and stored as cognitive memories.
 * The first implementation uses Ollama's audio-capable models; future
 * implementations may use OpenAI Whisper or other ASR systems.</p>
 *
 * <h3>Supported Formats</h3>
 * <ul>
 *   <li>MP3 ({@code audio/mpeg})</li>
 *   <li>WAV ({@code audio/wav}, {@code audio/wave})</li>
 *   <li>FLAC ({@code audio/flac})</li>
 *   <li>OGG ({@code audio/ogg})</li>
 *   <li>M4A ({@code audio/mp4}, {@code audio/x-m4a})</li>
 *   <li>WebM Audio ({@code audio/webm})</li>
 * </ul>
 *
 * <h3>Chunking Strategy</h3>
 * <p>Audio transcripts are typically returned as a single chunk (full transcript)
 * or segmented by silence/paragraph boundaries. Implementors may produce
 * multiple chunks for long audio files with natural break points.</p>
 *
 * @see SensoryExtractor
 */
public interface AudioTranscriptExtractor extends SensoryExtractor {

    /** Standard MIME types for audio formats. */
    Set<String> AUDIO_MIME_TYPES = Set.of(
            "audio/mpeg",       // MP3
            "audio/wav",        // WAV
            "audio/wave",       // WAV (alt)
            "audio/x-wav",      // WAV (alt)
            "audio/flac",       // FLAC
            "audio/ogg",        // OGG
            "audio/mp4",        // M4A/AAC
            "audio/x-m4a",      // M4A (alt)
            "audio/webm",       // WebM Audio
            "audio/aac"         // AAC
    );

    /**
     * Transcribes an audio file into text chunks.
     *
     * <p>Each chunk represents a segment of the transcript with metadata
     * including timestamps (if available), speaker identification, and
     * confidence scores.</p>
     *
     * @param source   path to the audio file
     * @param mimeType MIME type hint (may be null for auto-detection)
     * @return stream of transcript chunks
     * @throws IOException if the file cannot be read or transcribed
     */
    @Override
    Stream<ExtractionChunk> extract(Path source, String mimeType) throws IOException;

    @Override
    default Set<String> supportedMimeTypes() {
        return AUDIO_MIME_TYPES;
    }

    @Override
    default boolean supports(String mimeType) {
        if (mimeType == null) return false;
        return AUDIO_MIME_TYPES.contains(mimeType.toLowerCase())
                || mimeType.toLowerCase().startsWith("audio/");
    }

    /**
     * Returns the name of the transcription model being used.
     *
     * @return model name (e.g., "whisper", "gemma4", "qwen2-audio")
     */
    String transcriptionModel();

    /**
     * Transcription metadata keys added to ExtractionChunk metadata.
     */
    interface TranscriptMetadata {
        /** Duration of the audio segment in seconds. */
        String DURATION_SECONDS = "audio_duration_seconds";
        /** Start time of the segment in seconds (for chunked transcripts). */
        String START_TIME = "audio_start_time";
        /** End time of the segment in seconds. */
        String END_TIME = "audio_end_time";
        /** Language detected or specified. */
        String LANGUAGE = "audio_language";
        /** Confidence score of the transcription (0.0 to 1.0). */
        String CONFIDENCE = "audio_confidence";
        /** Speaker identifier (for multi-speaker audio). */
        String SPEAKER = "audio_speaker";
        /** Transcription model used. */
        String MODEL = "transcription_model";
    }
}
