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
package com.spectrayan.spector.synapse.security.injection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Intercepts user input, tool responses, and retrieved documents before they
 * reach the LLM context.
 *
 * <p>In {@code BLOCK} mode, direct user-input injections throw
 * {@link PromptInjectionException}. Indirect hits (tool output / documents)
 * are filtered out of the returned lists so a single poisoned chunk does not
 * abort an otherwise valid turn.</p>
 */
public final class InjectionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InjectionInterceptor.class);

    private static final String REDACTED_TOOL =
            "[tool_result redacted: prompt injection detected]";
    private static final String REDACTED_DOC =
            "[document redacted: prompt injection detected]";

    private final PromptShield shield;

    public InjectionInterceptor(PromptShield shield) {
        this.shield = Objects.requireNonNull(shield, "shield");
    }

    /**
     * Validates user input before cognitive / agentic graph processing.
     *
     * @param message raw user message
     * @return the same message when allowed
     * @throws PromptInjectionException when blocked
     */
    public String interceptUserInput(String message) {
        shield.inspect(message, InjectionSource.USER_INPUT);
        return message;
    }

    /**
     * Scans a single tool response. In BLOCK mode, returns a redacted placeholder
     * instead of the malicious payload (does not throw — keeps the tool loop alive).
     */
    public String interceptToolOutput(String toolOutput) {
        InjectionResult result = shield.evaluate(toolOutput, InjectionSource.TOOL_OUTPUT);
        if (result.detected() && result.blocked()) {
            log.warn("[InjectionInterceptor] Redacting tool output (pattern={})",
                    result.matchedPattern());
            return REDACTED_TOOL;
        }
        return toolOutput != null ? toolOutput : "";
    }

    /**
     * Scans a retrieved document / memory chunk.
     *
     * @return empty when the chunk should be excluded in BLOCK mode; otherwise the original text
     */
    public Optional<String> interceptDocument(String document) {
        InjectionResult result = shield.evaluate(document, InjectionSource.DOCUMENT);
        if (result.detected() && result.blocked()) {
            log.warn("[InjectionInterceptor] Excluding document chunk (pattern={})",
                    result.matchedPattern());
            return Optional.empty();
        }
        if (result.detected()) {
            // WARN: keep content but callers may annotate
            return Optional.of(document);
        }
        return Optional.ofNullable(document);
    }

    /**
     * Filters a list of context/document strings, dropping blocked indirect injections.
     */
    public List<String> filterDocuments(List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<String> kept = new ArrayList<>(documents.size());
        for (String doc : documents) {
            interceptDocument(doc).ifPresent(kept::add);
        }
        return kept;
    }

    /**
     * Annotates a blocked document for WARN telemetry (used in tests / UI).
     */
    public String redactedDocumentPlaceholder() {
        return REDACTED_DOC;
    }

    public PromptShield shield() {
        return shield;
    }
}
