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

import com.spectrayan.spector.kernel.store.codec.EpisodeCodec;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.kernel.engram.EncodingHeader;

/**
 * Conversation role — identifies the author/purpose of an episodic chat turn.
 *
 * <h3>Binary Encoding</h3>
 * <p>Stored in the {@code valence} byte (offset 2) of the
 * {@link com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields synaptic header}
 * when the record's {@link MemoryType} is {@code EPISODIC}. The same byte offset
 * is used for emotional valence in SEMANTIC/PROCEDURAL records — the
 * {@code MemoryType} bits in the flags byte (bits 1-2) determine interpretation.</p>
 *
 * <h3>Design Note</h3>
 * <p>Ordinal values are stable and must NOT be reordered. New roles may be
 * appended at the end. This enum participates in the binary header layout
 * and must remain backward-compatible across on-disk format versions.</p>
 *
 * @since 1.3.0
 * @see MemoryType#EPISODIC
 * @see com.spectrayan.spector.kernel.store.codec.EpisodeCodec
 */
public enum ConversationRole {

    /** User-authored message. */
    USER,

    /** LLM-generated response. */
    ASSISTANT,

    /** System prompt or instruction. */
    SYSTEM,

    /** Tool/function invocation request from the assistant. */
    TOOL_CALL,

    /** Tool/function execution result. */
    TOOL_RESULT,

    /** Internal reasoning trace (chain-of-thought). */
    THOUGHT;

    /** Maximum number of roles encodable in a single byte (0-255). */
    private static final ConversationRole[] VALUES = values();

    /**
     * Converts a byte ordinal (0-255) back to a {@code ConversationRole}.
     *
     * <p>Unknown ordinals default to {@code USER} for forward compatibility
     * (future header versions may add new roles).</p>
     *
     * @param ordinal the byte ordinal from the header's valence field
     * @return the corresponding role
     */
    public static ConversationRole fromOrdinal(int ordinal) {
        if (ordinal >= 0 && ordinal < VALUES.length) {
            return VALUES[ordinal];
        }
        return USER;
    }

    /**
     * Parses a role from a string name (case-insensitive).
     *
     * <p>Returns {@code USER} for null, blank, or unrecognized values.</p>
     *
     * @param name the role name (e.g., "ASSISTANT", "tool_call")
     * @return the corresponding role
     */
    public static ConversationRole fromName(String name) {
        if (name == null || name.isBlank()) return USER;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }
}
