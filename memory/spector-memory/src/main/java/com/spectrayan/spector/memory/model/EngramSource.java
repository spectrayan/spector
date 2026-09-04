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
package com.spectrayan.spector.memory.model;

/**
 * Trace provenance classification conforming to the MF-001 §NF7 (Source Honesty) rule.
 *
 * <p>Every durable trace has an immutable source in {@code {EXPERIENCED, DISTILLED, SIMULATED, REHEARSED}},
 * physically persisted in the binary engram encoding header at offset 46.</p>
 *
 * <h3>MF-001 §NF7 Constraints</h3>
 * <ul>
 *   <li><b>EXPERIENCED (0)</b>: Only {@code remember} from the physical world or user creates
 *       experienced traces. No constructive or simulated operation may write experienced.</li>
 *   <li><b>DISTILLED (1)</b>: Synthesized or consolidated traces produced by offline processing
 *       (e.g., reflection via {@code ReflectDaemon}, procedural skill crystallization, or
 *       {@code commit_simulation} with lineage).</li>
 *   <li><b>SIMULATED (2)</b>: Constructive counterfactual simulations, REM dreams, or hypothetical
 *       scenarios. Hard-gated and omitted by default during recall unless explicitly permitted.
 *       No operation promotes simulated into episodic experienced.</li>
 *   <li><b>REHEARSED (3)</b>: Traces transferred from external agent WALs or explicit replay.</li>
 * </ul>
 *
 * <p>Source honesty guarantees that the agent can distinguish between lived reality and imagined
 * scenarios (reality monitoring), preventing cognitive confabulation.</p>
 *
 * @see com.spectrayan.spector.memory.kernel.layout.EncodingHeader
 * @see com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields#OFFSET_V2_SOURCE
 */
public enum EngramSource {

    /**
     * Lived experience directly observed or user-stated.
     * Only {@code remember} from the world or user creates experienced traces.
     */
    EXPERIENCED((byte) 0, "experienced"),

    /**
     * Distilled or consolidated knowledge synthesized from prior memories.
     */
    DISTILLED((byte) 1, "distilled"),

    /**
     * Counterfactual simulation, dream cycle, or hypothetical projection.
     * Omitted by default during standard recall.
     */
    SIMULATED((byte) 2, "simulated"),

    /**
     * Transferred from another agent's journal or rehearsed via replay.
     */
    REHEARSED((byte) 3, "rehearsed");

    private final byte code;
    private final String label;

    EngramSource(byte code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Returns the 1-byte unsigned representation stored at offset 46 in the encoding header.
     *
     * @return numeric source code (0..3)
     */
    public byte code() {
        return code;
    }

    /**
     * Returns the canonical string label specified by MF-001 §NF7.
     *
     * @return canonical lowercase label
     */
    public String label() {
        return label;
    }

    /**
     * Resolves an {@code EngramSource} from its 1-byte binary code.
     * Defaults to {@link #EXPERIENCED} if unknown.
     *
     * @param code byte code from encoding header
     * @return corresponding EngramSource
     */
    public static EngramSource fromCode(byte code) {
        return switch (code) {
            case 0 -> EXPERIENCED;
            case 1 -> DISTILLED;
            case 2 -> SIMULATED;
            case 3 -> REHEARSED;
            default -> EXPERIENCED;
        };
    }

    /**
     * Resolves an {@code EngramSource} from an ordinal integer.
     *
     * @param ordinal ordinal value
     * @return corresponding EngramSource
     */
    public static EngramSource fromOrdinal(int ordinal) {
        return fromCode((byte) ordinal);
    }

    /**
     * Parses a string label into an {@code EngramSource} (case-insensitive).
     *
     * @param str string label
     * @return corresponding EngramSource, or EXPERIENCED if null/unrecognized
     */
    public static EngramSource fromString(String str) {
        if (str == null || str.isBlank()) {
            return EXPERIENCED;
        }
        return switch (str.trim().toLowerCase()) {
            case "distilled", "reflected", "crystallized" -> DISTILLED;
            case "simulated", "dreamed", "thought_experiment", "langevin_discovery", "inferred" -> SIMULATED;
            case "rehearsed", "transferred" -> REHEARSED;
            case "experienced", "observed", "user_stated" -> EXPERIENCED;
            default -> EXPERIENCED;
        };
    }
}
