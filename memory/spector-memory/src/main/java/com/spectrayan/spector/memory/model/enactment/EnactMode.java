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
package com.spectrayan.spector.memory.model.enactment;

/**
 * Modes of persona enactment (ADR-0032).
 */
public enum EnactMode {
    /** Conversational embodiment under FACT tense; read-only tools allowed. */
    REACT,
    /** Goal-directed execution under FACT tense; side-effecting tools gated by stance. */
    DECIDE,
    /** What-if governance simulation under SIM tense; zero side-effects. */
    SIMULATE,
    /** Historical reconstruction at time tau under REPLAY tense. */
    REPLAY
}
