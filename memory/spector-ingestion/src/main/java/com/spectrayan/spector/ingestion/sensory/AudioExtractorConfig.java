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

/**
 * Configuration for audio transcription extractors.
 *
 * <p>Used by {@link OllamaAudioExtractor} and future audio transcription
 * implementations (Whisper, cloud ASR services, etc.).</p>
 *
 * @param model            model name for audio transcription (e.g., "gemma4", "qwen2-audio")
 * @param baseUrl          API base URL (default: "http://localhost:11434" for Ollama)
 * @param timeoutSeconds   maximum time for a single transcription request
 * @param maxSegmentSeconds maximum audio segment length before splitting (0 = no splitting)
 * @param language         hint for expected language (null = auto-detect)
 */
public record AudioExtractorConfig(
        String model,
        String baseUrl,
        int timeoutSeconds,
        int maxSegmentSeconds,
        String language
) {
    /** Default configuration for Ollama-based audio extraction. */
    public static final AudioExtractorConfig DEFAULT = new AudioExtractorConfig(
            "gemma4", null, 120, 0, null
    );

    /** Creates config with just a model name, using defaults for everything else. */
    public static AudioExtractorConfig ofModel(String model) {
        return new AudioExtractorConfig(model, DEFAULT.baseUrl, DEFAULT.timeoutSeconds,
                DEFAULT.maxSegmentSeconds, DEFAULT.language);
    }

    /** Creates config with model and base URL. */
    public static AudioExtractorConfig of(String model, String baseUrl) {
        return new AudioExtractorConfig(model, baseUrl, DEFAULT.timeoutSeconds,
                DEFAULT.maxSegmentSeconds, DEFAULT.language);
    }

    /** Creates config with model, base URL, and timeout. */
    public static AudioExtractorConfig of(String model, String baseUrl, int timeoutSeconds) {
        return new AudioExtractorConfig(model, baseUrl, timeoutSeconds,
                DEFAULT.maxSegmentSeconds, DEFAULT.language);
    }

    public AudioExtractorConfig {
        if (model == null || model.isBlank()) model = DEFAULT.model;
        if (baseUrl != null && baseUrl.isBlank()) baseUrl = null;
        if (timeoutSeconds <= 0) timeoutSeconds = DEFAULT.timeoutSeconds;
        if (maxSegmentSeconds < 0) maxSegmentSeconds = 0;
    }

    /** Returns the effective base URL, stripping trailing slash. */
    public String effectiveBaseUrl() {
        if (baseUrl == null) return null;
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
