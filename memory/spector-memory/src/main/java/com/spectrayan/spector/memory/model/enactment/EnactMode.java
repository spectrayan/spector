/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
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
