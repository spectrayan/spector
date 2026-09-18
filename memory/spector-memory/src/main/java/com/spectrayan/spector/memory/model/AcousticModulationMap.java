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
package com.spectrayan.spector.memory.model;

public record AcousticModulationMap(
        float pitchArousalSensitivity,
        float pitchValenceSensitivity,
        float tempoArousalSensitivity,
        float tempoValenceSensitivity,
        float varianceArousalSensitivity,
        float breathinessDominanceSensitivity,
        float assertivenessDominanceSensitivity
) {
    public static final AcousticModulationMap DEFAULT = new AcousticModulationMap(
            25.0f, 10.0f, 0.25f, 0.05f, 0.35f, 0.40f, 0.30f
    );
}
