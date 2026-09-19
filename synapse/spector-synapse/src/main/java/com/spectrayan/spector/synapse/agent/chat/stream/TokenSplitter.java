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
package com.spectrayan.spector.synapse.agent.chat.stream;

import com.spectrayan.spector.synapse.agent.graph.AgentChatListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Stateful stream processor that demultiplexes raw streaming text chunks from
 * an LLM into discrete thinking events and visible token events (Issue #263, ADR-0084).
 *
 * <p>Key Invariants:
 * <ul>
 *   <li>Buffers tag prefix characters (up to 8 characters) to guarantee delimiter
 *       tags ({@code <think>} and {@code </think>}) never leak into visible tokens.</li>
 *   <li>Routes native reasoning deltas (via {@link #onNativeThinking(String)}) to thinking output.</li>
 *   <li>Extracts fallback {@code <think>...</think>} reasoning blocks from the text stream.</li>
 *   <li>Strict Invariant: No Chain-of-Thought (CoT) or raw tool JSON may ever leak into {@code token} events.</li>
 * </ul>
 */
public final class TokenSplitter {

    private static final Logger log = LoggerFactory.getLogger(TokenSplitter.class);

    public static final String OPEN_TAG = "<think>";
    public static final String CLOSE_TAG = "</think>";

    public enum State {
        IDLE,
        THINKING,
        TEXT,
        TOOL_PENDING
    }

    private final BiConsumer<String, Long> thinkingConsumer;
    private final Consumer<String> tokenConsumer;

    private State state = State.IDLE;
    private final StringBuilder tagBuffer = new StringBuilder(16);
    private int thinkingDepth = 0;
    private long thinkingStartNano = 0L;

    /**
     * Constructs a TokenSplitter wired to an {@link AgentChatListener}.
     */
    public TokenSplitter(AgentChatListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        this.thinkingConsumer = listener::onThinking;
        this.tokenConsumer = listener::onToken;
    }

    /**
     * Constructs a TokenSplitter with custom thinking and token consumers.
     */
    public TokenSplitter(BiConsumer<String, Long> thinkingConsumer, Consumer<String> tokenConsumer) {
        this.thinkingConsumer = Objects.requireNonNull(thinkingConsumer, "thinkingConsumer must not be null");
        this.tokenConsumer = Objects.requireNonNull(tokenConsumer, "tokenConsumer must not be null");
    }

    /**
     * Ingests a native reasoning delta from the LLM provider.
     */
    public synchronized void onNativeThinking(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (tagBuffer.length() > 0) {
            if (state == State.THINKING) {
                emitThinking(tagBuffer.toString());
            } else {
                emitToken(tagBuffer.toString());
            }
            tagBuffer.setLength(0);
        }
        if (state != State.THINKING) {
            this.state = State.THINKING;
            this.thinkingDepth = 0;
            if (this.thinkingStartNano == 0L) {
                this.thinkingStartNano = System.nanoTime();
            }
        }
        emitThinking(text);
    }

    /**
     * Ingests a standard text chunk from the LLM stream.
     */
    public synchronized void onTextChunk(String chunk) {
        if (chunk == null || chunk.isEmpty() || state == State.TOOL_PENDING) {
            return;
        }

        if (state == State.THINKING && thinkingDepth == 0) {
            this.state = State.IDLE;
        }

        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (state == State.THINKING) {
                processCharInThinking(c);
            } else {
                processCharInText(c);
            }
        }
    }

    /**
     * Signals that a tool execution call has been initiated by the LLM.
     * Pauses visible token streaming and transitions to TOOL_PENDING.
     */
    public synchronized void onToolCall() {
        if (tagBuffer.length() > 0) {
            if (state == State.THINKING) {
                emitThinking(tagBuffer.toString());
            } else {
                emitToken(tagBuffer.toString());
            }
            tagBuffer.setLength(0);
        }
        this.state = State.TOOL_PENDING;
    }

    /**
     * Resumes token streaming after tool execution has completed.
     */
    public synchronized void onToolResume() {
        this.state = State.IDLE;
    }

    /**
     * Flushes any buffered characters at stream completion.
     */
    public synchronized void flush() {
        if (tagBuffer.length() > 0) {
            String pending = tagBuffer.toString();
            tagBuffer.setLength(0);
            if (state == State.THINKING) {
                emitThinking(pending);
            } else if (state == State.TEXT || state == State.IDLE) {
                emitToken(pending);
            }
        }
        this.state = State.IDLE;
        this.thinkingDepth = 0;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized int getBufferLength() {
        return tagBuffer.length();
    }

    // ── Internal State Machine ─────────────────────────────────────

    private void processCharInText(char c) {
        tagBuffer.append(c);
        String candidate = tagBuffer.toString();

        if (candidate.equals(OPEN_TAG)) {
            tagBuffer.setLength(0);
            transitionToThinking();
            return;
        }

        if (OPEN_TAG.startsWith(candidate)) {
            // Valid prefix of <think>, hold in buffer
            return;
        }

        // Divergence: find longest proper suffix matching OPEN_TAG prefix
        String matchingSuffix = findLongestPrefixSuffix(candidate, OPEN_TAG);
        if (matchingSuffix != null) {
            String toEmit = candidate.substring(0, candidate.length() - matchingSuffix.length());
            emitToken(toEmit);
            tagBuffer.setLength(0);
            tagBuffer.append(matchingSuffix);
        } else {
            emitToken(candidate);
            tagBuffer.setLength(0);
        }

        if (state == State.IDLE) {
            state = State.TEXT;
        }
    }

    private void processCharInThinking(char c) {
        tagBuffer.append(c);
        String candidate = tagBuffer.toString();

        if (candidate.equals(CLOSE_TAG)) {
            tagBuffer.setLength(0);
            thinkingDepth--;
            if (thinkingDepth <= 0) {
                thinkingDepth = 0;
                this.state = State.TEXT;
            }
            return;
        }

        if (candidate.equals(OPEN_TAG)) {
            // Nested thinking tag
            tagBuffer.setLength(0);
            thinkingDepth++;
            return;
        }

        if (CLOSE_TAG.startsWith(candidate) || OPEN_TAG.startsWith(candidate)) {
            // Valid prefix of </think> or nested <think>, hold in buffer
            return;
        }

        // Divergence: find longest proper suffix matching CLOSE_TAG or OPEN_TAG
        String matchingSuffix = findLongestPrefixSuffix(candidate, CLOSE_TAG, OPEN_TAG);
        if (matchingSuffix != null) {
            String toEmit = candidate.substring(0, candidate.length() - matchingSuffix.length());
            emitThinking(toEmit);
            tagBuffer.setLength(0);
            tagBuffer.append(matchingSuffix);
        } else {
            emitThinking(candidate);
            tagBuffer.setLength(0);
        }
    }

    private void transitionToThinking() {
        this.state = State.THINKING;
        this.thinkingDepth = 1;
        this.thinkingStartNano = System.nanoTime();
    }

    private void emitThinking(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        long elapsedMs = thinkingStartNano > 0
                ? (System.nanoTime() - thinkingStartNano) / 1_000_000L
                : 0L;
        thinkingConsumer.accept(text, elapsedMs);
    }

    private void emitToken(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        tokenConsumer.accept(text);
    }

    private static String findLongestPrefixSuffix(String candidate, String targetTag) {
        for (int len = candidate.length() - 1; len >= 1; len--) {
            String suffix = candidate.substring(candidate.length() - len);
            if (targetTag.startsWith(suffix)) {
                return suffix;
            }
        }
        return null;
    }

    private static String findLongestPrefixSuffix(String candidate, String tag1, String tag2) {
        for (int len = candidate.length() - 1; len >= 1; len--) {
            String suffix = candidate.substring(candidate.length() - len);
            if (tag1.startsWith(suffix) || tag2.startsWith(suffix)) {
                return suffix;
            }
        }
        return null;
    }
}
