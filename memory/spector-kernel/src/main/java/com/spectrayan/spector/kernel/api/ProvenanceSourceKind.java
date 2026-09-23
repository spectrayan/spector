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
package com.spectrayan.spector.kernel.api;

/**
 * Provenance source classification for Region 26 (Provenance Slab).
 *
 * <p>Identifies the origin tier or artifact from which a consolidated
 * semantic fact or crystallized procedural skill was derived.</p>
 *
 * <h3>Historical Note</h3>
 * <p>Originally named {@code EPISODIC_LOG} in ADR-0029; standardized to
 * {@code EPISODIC} to align with the core {@link MemoryType} taxonomy.</p>
 *
 * @see com.spectrayan.spector.kernel.layout.ProvenanceLayout
 * @since 1.5.0
 */
public enum ProvenanceSourceKind {

    /**
     * Source is an episodic session / turn sequence.
     * Formerly designated {@code EPISODIC_LOG}.
     */
    EPISODIC((byte) 1, "episodic"),

    /**
     * Source is an existing semantic memory / fact engram (ADR-0086 §5.5).
     */
    SEMANTIC((byte) 2, "semantic"),

    /**
     * Source is an existing procedural memory / crystallized skill (ADR-0086 §5.5).
     */
    PROCEDURAL((byte) 3, "procedural");

    private final byte code;
    private final String label;

    ProvenanceSourceKind(byte code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Returns the 1-byte unsigned code stored at offset 1 in the provenance record.
     *
     * @return numeric source code (1..3)
     */
    public byte code() {
        return code;
    }

    /**
     * Returns the canonical lowercase string label.
     *
     * @return canonical label
     */
    public String label() {
        return label;
    }

    /**
     * Resolves a {@code ProvenanceSourceKind} from its 1-byte binary code.
     *
     * @param code byte code from provenance record
     * @return corresponding ProvenanceSourceKind
     * @throws IllegalArgumentException if the code is unknown
     */
    public static ProvenanceSourceKind fromCode(byte code) {
        return switch (code) {
            case 1 -> EPISODIC;
            case 2 -> SEMANTIC;
            case 3 -> PROCEDURAL;
            default -> throw new IllegalArgumentException("Unknown provenance source code: " + code);
        };
    }

    /**
     * Resolves a {@code ProvenanceSourceKind} from an integer ordinal/code.
     *
     * @param code numeric code
     * @return corresponding ProvenanceSourceKind
     */
    public static ProvenanceSourceKind fromOrdinal(int code) {
        return fromCode((byte) code);
    }

    /**
     * Parses a string label or enum name (case-insensitive).
     * Recognizes legacy alias {@code "episodic_log"}.
     *
     * @param str string label or name
     * @return corresponding ProvenanceSourceKind, or EPISODIC if null/unrecognized
     */
    public static ProvenanceSourceKind fromString(String str) {
        if (str == null || str.isBlank()) {
            return EPISODIC;
        }
        return switch (str.trim().toLowerCase()) {
            case "semantic" -> SEMANTIC;
            case "procedural" -> PROCEDURAL;
            case "episodic", "episodic_log" -> EPISODIC;
            default -> EPISODIC;
        };
    }
}
