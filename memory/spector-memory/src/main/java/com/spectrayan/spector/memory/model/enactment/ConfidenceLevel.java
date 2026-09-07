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
 * Epistemic confidence levels for persona enactment (ADR-0032).
 */
public enum ConfidenceLevel {
    /** Grounded in strong procedural playbooks or waking episodic majority. */
    EVIDENCED,
    /** Conflicting evidence resolved via Expected Free Energy policy selection. */
    MIXED,
    /** Thin or missing memory precedents; inferred from baseline priors with mandatory hedging. */
    INFERRED
}
