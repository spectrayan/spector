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

import java.io.Serializable;
import java.util.Locale;

/**
 * Local affect appraisal ({@code spector.memory.affect.*}).
 *
 * <p>Off by default. {@link Mode#SOUL_ANCHORS} fills valence and arousal from four
 * embedding centroids when neither the caller nor a configured LLM set them.</p>
 */
public class AffectProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Who supplies valence and arousal when the caller leaves them unset. */
    public enum Mode {
        /** Leave unset values at zero. */
        NONE,
        /** A configured LLM tag extractor owns affect. Anchors stay off. */
        LLM,
        /** Embed the bundled or configured anchor phrases and score the memory vector. */
        SOUL_ANCHORS
    }

    public static final float DEFAULT_DEADBAND = 0.02f;
    public static final float DEFAULT_GAIN = 4.0f;

    private Mode appraiser = Mode.NONE;
    private String anchorsPath = "";
    private float deadband = DEFAULT_DEADBAND;
    private float gain = DEFAULT_GAIN;

    public Mode getAppraiser() { return appraiser; }

    public AffectProperties setAppraiser(Mode appraiser) {
        if (appraiser != null) {
            this.appraiser = appraiser;
        }
        return this;
    }

    /**
     * Accepts {@code none}, {@code llm}, and {@code soul-anchors}.
     *
     * @param raw the configured name
     * @return this
     * @throws IllegalArgumentException when {@code raw} is not one of the modes
     */
    public AffectProperties setAppraiser(String raw) {
        if (raw == null || raw.isBlank()) {
            return this;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return setAppraiser(Mode.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "spector.memory.affect.appraiser '" + raw + "' is not one of none, llm, soul-anchors", e);
        }
    }

    public String getAnchorsPath() { return anchorsPath; }

    public AffectProperties setAnchorsPath(String anchorsPath) {
        this.anchorsPath = anchorsPath != null ? anchorsPath : "";
        return this;
    }

    public float getDeadband() { return deadband; }

    public AffectProperties setDeadband(float deadband) {
        this.deadband = deadband;
        return this;
    }

    public float getGain() { return gain; }

    public AffectProperties setGain(float gain) {
        this.gain = gain;
        return this;
    }

    public AffectProperties copy() {
        AffectProperties cp = new AffectProperties();
        cp.appraiser = this.appraiser;
        cp.anchorsPath = this.anchorsPath;
        cp.deadband = this.deadband;
        cp.gain = this.gain;
        return cp;
    }
}
