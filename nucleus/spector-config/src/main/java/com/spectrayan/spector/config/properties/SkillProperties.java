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

import java.io.Serializable;

/**
 * Configuration properties for procedural skill crystallization (ADR-0086 §7).
 */
public class SkillProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private int minSessions = DEFAULT_MEMORY_SKILL_MIN_SESSIONS;
    private float duplicateCosine = DEFAULT_MEMORY_SKILL_DUPLICATE_COSINE;
    private boolean allowSemanticOnly = DEFAULT_MEMORY_SKILL_ALLOW_SEMANTIC_ONLY;
    private float utilityAlpha = DEFAULT_MEMORY_SKILL_UTILITY_ALPHA;

    public int getMinSessions() {
        return minSessions;
    }

    public void setMinSessions(int minSessions) {
        this.minSessions = minSessions;
    }

    public float getDuplicateCosine() {
        return duplicateCosine;
    }

    public void setDuplicateCosine(float duplicateCosine) {
        this.duplicateCosine = duplicateCosine;
    }

    public boolean isAllowSemanticOnly() {
        return allowSemanticOnly;
    }

    public void setAllowSemanticOnly(boolean allowSemanticOnly) {
        this.allowSemanticOnly = allowSemanticOnly;
    }

    public float getUtilityAlpha() {
        return utilityAlpha;
    }

    public void setUtilityAlpha(float utilityAlpha) {
        this.utilityAlpha = utilityAlpha;
    }

    public float duplicateCosine() {
        return duplicateCosine;
    }

    public int minSessions() {
        return minSessions;
    }

    public float utilityAlpha() {
        return utilityAlpha;
    }
}
