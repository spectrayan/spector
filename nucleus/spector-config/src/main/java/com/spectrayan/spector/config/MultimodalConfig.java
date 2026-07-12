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
package com.spectrayan.spector.config;

import java.nio.file.Path;

/**
 * Configuration for Spector's multimodal processing pipeline.
 *
 * <p>Loaded from the {@code spector.multimodal} section of {@code spector.yml}:</p>
 * <pre>{@code
 *   spector:
 *     multimodal:
 *       enabled: true
 *       vision:
 *         model: "llava"
 *         base-url: "http://localhost:11434"
 *         timeout-seconds: 120
 *       audio:
 *         model: "gemma4"
 *         timeout-seconds: 180
 *       asset-store:
 *         type: "local"
 *         base-path: ".spector/assets"
 * }</pre>
 *
 * @param enabled          whether multimodal processing is enabled
 * @param visionModel      Ollama vision model name (e.g., "llava", "moondream")
 * @param visionBaseUrl    Ollama base URL for vision requests
 * @param visionTimeout    timeout in seconds for vision requests
 * @param audioModel       Ollama audio model name (e.g., "gemma4", "qwen2-audio")
 * @param audioTimeout     timeout in seconds for audio transcription
 * @param assetStoreType   asset store implementation type ("local", "s3", "gcs")
 * @param assetBasePath    base path/directory for asset storage
 */
public record MultimodalConfig(
        boolean enabled,
        String visionModel,
        String visionBaseUrl,
        int visionTimeout,
        String audioModel,
        int audioTimeout,
        String assetStoreType,
        Path assetBasePath
) {
    /** Default multimodal configuration (enabled, moondream on localhost). */
    public static final MultimodalConfig DEFAULT = new MultimodalConfig(
            false,
            "moondream",
            "http://localhost:11434",
            120,
            "gemma4",
            180,
            "local",
            Path.of(".spector", "assets")
    );

    /**
     * Creates a {@link MultimodalConfig} from hierarchical properties.
     *
     * @param props the hierarchical properties (should be scoped to root level)
     * @return configured MultimodalConfig
     */
    public static MultimodalConfig from(SpectorProperties props) {
        return new MultimodalConfig(
                props.getBoolean("spector.multimodal.enabled", DEFAULT.enabled),
                props.getString("spector.multimodal.vision.model", DEFAULT.visionModel),
                props.getString("spector.multimodal.vision.base-url", DEFAULT.visionBaseUrl),
                props.getInt("spector.multimodal.vision.timeout-seconds", DEFAULT.visionTimeout),
                props.getString("spector.multimodal.audio.model", DEFAULT.audioModel),
                props.getInt("spector.multimodal.audio.timeout-seconds", DEFAULT.audioTimeout),
                props.getString("spector.multimodal.asset-store.type", DEFAULT.assetStoreType),
                props.getPath("spector.multimodal.asset-store.base-path", DEFAULT.assetBasePath)
        );
    }

    /** Returns whether vision processing is configured. */
    public boolean isVisionConfigured() {
        return enabled && visionModel != null && !visionModel.isBlank();
    }

    /** Returns whether audio processing is configured. */
    public boolean isAudioConfigured() {
        return enabled && audioModel != null && !audioModel.isBlank();
    }

    /** Returns whether asset storage uses local filesystem. */
    public boolean isLocalAssetStore() {
        return "local".equalsIgnoreCase(assetStoreType);
    }
}
