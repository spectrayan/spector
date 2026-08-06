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
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import com.spectrayan.spector.config.SpectorProperties;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for Spector's multimodal processing pipeline.
 */
public class MultimodalProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final MultimodalProperties DEFAULT = new MultimodalProperties(
            DEFAULT_MULTIMODAL_ENABLED,
            DEFAULT_MULTIMODAL_VISION_MODEL,
            DEFAULT_MULTIMODAL_VISION_BASE_URL,
            DEFAULT_MULTIMODAL_VISION_TIMEOUT,
            DEFAULT_MULTIMODAL_AUDIO_MODEL,
            DEFAULT_MULTIMODAL_AUDIO_TIMEOUT,
            DEFAULT_MULTIMODAL_ASSET_STORE_TYPE,
            DEFAULT_MULTIMODAL_ASSET_BASE_PATH
    );

    private boolean enabled;
    private String visionModel;
    private String visionBaseUrl;
    private int visionTimeout;
    private String audioModel;
    private int audioTimeout;
    private String assetStoreType;
    private Path assetBasePath;

    public MultimodalProperties() {
        this(DEFAULT.enabled, DEFAULT.visionModel, DEFAULT.visionBaseUrl, DEFAULT.visionTimeout,
                DEFAULT.audioModel, DEFAULT.audioTimeout, DEFAULT.assetStoreType, DEFAULT.assetBasePath);
    }

    public MultimodalProperties(boolean enabled, String visionModel, String visionBaseUrl,
                                int visionTimeout, String audioModel, int audioTimeout,
                                String assetStoreType, Path assetBasePath) {
        this.enabled = enabled;
        this.visionModel = visionModel;
        this.visionBaseUrl = visionBaseUrl;
        this.visionTimeout = visionTimeout;
        this.audioModel = audioModel;
        this.audioTimeout = audioTimeout;
        this.assetStoreType = assetStoreType;
        this.assetBasePath = assetBasePath;
    }

    public static MultimodalProperties from(SpectorProperties props) {
        if (props == null) return DEFAULT;
        return new MultimodalProperties(
                props.getBoolean(MULTIMODAL_ENABLED, DEFAULT.enabled),
                props.getString(MULTIMODAL_VISION_MODEL, DEFAULT.visionModel),
                props.getString(MULTIMODAL_VISION_BASE_URL, DEFAULT.visionBaseUrl),
                props.getInt(MULTIMODAL_VISION_TIMEOUT, DEFAULT.visionTimeout),
                props.getString(MULTIMODAL_AUDIO_MODEL, DEFAULT.audioModel),
                props.getInt(MULTIMODAL_AUDIO_TIMEOUT, DEFAULT.audioTimeout),
                props.getString(MULTIMODAL_ASSET_STORE_TYPE, DEFAULT.assetStoreType),
                props.getPath(MULTIMODAL_ASSET_BASE_PATH, DEFAULT.assetBasePath)
        );
    }

    public boolean isVisionConfigured() {
        return enabled && visionModel != null && !visionModel.isBlank();
    }

    public boolean isAudioConfigured() {
        return enabled && audioModel != null && !audioModel.isBlank();
    }

    public boolean isLocalAssetStore() {
        return "local".equalsIgnoreCase(assetStoreType);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }

    public String getVisionModel() { return visionModel; }
    public void setVisionModel(String visionModel) { this.visionModel = visionModel; }
    public String visionModel() { return visionModel; }

    public String getVisionBaseUrl() { return visionBaseUrl; }
    public void setVisionBaseUrl(String visionBaseUrl) { this.visionBaseUrl = visionBaseUrl; }
    public String visionBaseUrl() { return visionBaseUrl; }

    public int getVisionTimeout() { return visionTimeout; }
    public void setVisionTimeout(int visionTimeout) { this.visionTimeout = visionTimeout; }
    public int visionTimeout() { return visionTimeout; }

    public String getAudioModel() { return audioModel; }
    public void setAudioModel(String audioModel) { this.audioModel = audioModel; }
    public String audioModel() { return audioModel; }

    public int getAudioTimeout() { return audioTimeout; }
    public void setAudioTimeout(int audioTimeout) { this.audioTimeout = audioTimeout; }
    public int audioTimeout() { return audioTimeout; }

    public String getAssetStoreType() { return assetStoreType; }
    public void setAssetStoreType(String assetStoreType) { this.assetStoreType = assetStoreType; }
    public String assetStoreType() { return assetStoreType; }

    public Path getAssetBasePath() { return assetBasePath; }
    public void setAssetBasePath(Path assetBasePath) { this.assetBasePath = assetBasePath; }
    public Path assetBasePath() { return assetBasePath; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MultimodalProperties that = (MultimodalProperties) o;
        return enabled == that.enabled && visionTimeout == that.visionTimeout &&
                audioTimeout == that.audioTimeout &&
                Objects.equals(visionModel, that.visionModel) &&
                Objects.equals(visionBaseUrl, that.visionBaseUrl) &&
                Objects.equals(audioModel, that.audioModel) &&
                Objects.equals(assetStoreType, that.assetStoreType) &&
                Objects.equals(assetBasePath, that.assetBasePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, visionModel, visionBaseUrl, visionTimeout,
                audioModel, audioTimeout, assetStoreType, assetBasePath);
    }

    @Override
    public String toString() {
        return "MultimodalProperties{" +
                "enabled=" + enabled +
                ", visionModel='" + visionModel + '\'' +
                ", visionBaseUrl='" + visionBaseUrl + '\'' +
                ", visionTimeout=" + visionTimeout +
                ", audioModel='" + audioModel + '\'' +
                ", audioTimeout=" + audioTimeout +
                ", assetStoreType='" + assetStoreType + '\'' +
                ", assetBasePath=" + assetBasePath +
                '}';
    }
}
