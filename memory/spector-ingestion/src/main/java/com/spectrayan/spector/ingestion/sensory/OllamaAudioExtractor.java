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

import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.AudioContent;
import com.spectrayan.spector.provider.model.ChatMessage;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.TextContent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Audio transcription extractor using Ollama multimodal models.
 *
 * <p>Uses Ollama models that support audio input (e.g., gemma4 with audio support,
 * qwen2-audio) to transcribe audio files into text. The audio file is encoded
 * and sent to the model as part of the prompt.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   LlmProvider llm = ... // create provider
 *   var extractor = new OllamaAudioExtractor(llm);
 *   
 *   try (Stream<ExtractionChunk> chunks = extractor.extract(audioPath, "audio/mpeg")) {
 *       chunks.forEach(chunk -> System.out.println(chunk.text()));
 *   }
 * }</pre>
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Requires an Ollama model with audio understanding capabilities</li>
 *   <li>Large audio files may need to be split before processing</li>
 *   <li>Quality depends on the underlying model's audio understanding</li>
 * </ul>
 */
public final class OllamaAudioExtractor implements AudioTranscriptExtractor {

    private static final Logger log = LoggerFactory.getLogger(OllamaAudioExtractor.class);

    /** Maximum file size for direct processing (50 MB). */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    private static final String TRANSCRIPTION_PROMPT = """
            Listen to this audio carefully and provide a complete, accurate transcription.
            Include all spoken words and identify different speakers if present.
            Format the output as plain text with paragraph breaks at natural pauses.
            Do not add commentary or analysis  --  only transcribe what is spoken.
            """;

    private final LlmProvider llm;
    private final String modelName;

    /**
     * Creates an audio extractor backed by an Ollama LLM provider.
     *
     * @param llm the text generation provider (must support audio input)
     */
    public OllamaAudioExtractor(LlmProvider llm) {
        if (llm == null) throw new IllegalArgumentException("LLM provider is required");
        this.llm = llm;
        this.modelName = llm.modelName();
        log.info("OllamaAudioExtractor initialized with model: {}", modelName);
    }

    @Override
    public Stream<ExtractionChunk> extract(Path source, String mimeType) throws IOException {
        if (source == null || !Files.exists(source)) {
            throw new IOException("Audio file does not exist: " + source);
        }

        long fileSize = Files.size(source);
        if (fileSize == 0) {
            log.debug("Skipping empty audio file: {}", source.getFileName());
            return Stream.empty();
        }

        if (fileSize > MAX_FILE_SIZE) {
            throw new IOException("Audio file too large (" + fileSize + " bytes, max " +
                    MAX_FILE_SIZE + "): " + source.getFileName());
        }

        log.info("Transcribing audio: {} ({}B, mime={})", source.getFileName(), fileSize, mimeType);

        byte[] audioBytes = Files.readAllBytes(source);

        String prompt = TRANSCRIPTION_PROMPT + "\n[Audio file: " + source.getFileName() +
                ", size: " + fileSize + " bytes, type: " +
                (mimeType != null ? mimeType : "auto-detect") + "]";

        String transcript;
        try {
            var message = ChatMessage.user(
                new TextContent(prompt),
                new AudioContent(audioBytes, mimeType, null)
            );
            var request = LlmRequest.fromMessages(List.of(message));
            
            var response = llm.generate(request);
            transcript = response.text();
        } catch (LlmProvider.GenerationException e) {
            throw new IOException("Audio transcription failed for " + source.getFileName() +
                    ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException("Audio transcription failed for " + source.getFileName() +
                    ": " + e.getMessage(), e);
        }

        if (transcript == null || transcript.isBlank()) {
            log.warn("Empty transcript for audio: {}", source.getFileName());
            return Stream.empty();
        }

        // Build metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("modality", "AUDIO");
        metadata.put("source_uri", source.toUri().toString());
        metadata.put("original_filename", source.getFileName().toString());
        metadata.put("extractor", "ollama-audio");
        metadata.put(AudioTranscriptExtractor.TranscriptMetadata.MODEL, modelName);
        if (mimeType != null) metadata.put("content_type", mimeType);
        metadata.put("file_size_bytes", String.valueOf(fileSize));

        log.info("Transcribed {}B audio  ->  {}B text using {}", fileSize, transcript.length(), modelName);

        return Stream.of(new ExtractionChunk("transcript-0", transcript.strip(), metadata));
    }

    @Override
    public boolean isAvailable() {
        return llm.isAvailable();
    }

    @Override
    public String transcriptionModel() {
        return modelName;
    }
}
