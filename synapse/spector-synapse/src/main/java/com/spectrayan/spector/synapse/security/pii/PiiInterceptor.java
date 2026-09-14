/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.security.pii;

import com.spectrayan.spector.synapse.security.config.SecurityProperties.PiiProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Intercepts text before LLM provider calls: redact outbound PII, rehydrate
 * tokens in inbound responses.
 *
 * <p>An active {@link PiiRedactionSession} is bound to the calling thread for
 * the duration of {@link #protect(String, Function)} so nested sites
 * (tool output, retrieved documents) can contribute to the same token map via
 * {@link #redactUsingActiveSession(String)}.</p>
 */
public final class PiiInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PiiInterceptor.class);

    private static final ThreadLocal<PiiRedactionSession> ACTIVE = new ThreadLocal<>();

    private final PiiProperties config;
    private final PiiRedactor redactor;
    private final PiiRehydrator rehydrator;

    public PiiInterceptor(PiiProperties config) {
        this(config, new PiiRedactor(), new PiiRehydrator());
    }

    public PiiInterceptor(PiiProperties config, PiiRedactor redactor, PiiRehydrator rehydrator) {
        this.config = Objects.requireNonNullElseGet(config, PiiProperties::new);
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.rehydrator = Objects.requireNonNull(rehydrator, "rehydrator");
    }

    public boolean isActive() {
        return config.isActive();
    }

    public PiiProperties config() {
        return config;
    }

    /**
     * Redacts {@code input}, invokes {@code llmCall} with the redacted text,
     * then rehydrates tokens in the response.
     */
    public String protect(String input, Function<String, String> llmCall) {
        Objects.requireNonNull(llmCall, "llmCall");
        if (!isActive()) {
            return llmCall.apply(input);
        }

        PiiRedactionSession session = new PiiRedactionSession();
        ACTIVE.set(session);
        try {
            PiiRedactionResult outbound = redactor.redact(input, config.getLevel(), session);
            if (outbound.hadPii()) {
                log.info("[PiiInterceptor] Outbound redaction typeCounts={}", outbound.typeCounts());
            }
            String response = llmCall.apply(outbound.redactedText());
            return rehydrator.rehydrate(response, session);
        } finally {
            ACTIVE.remove();
        }
    }

    /**
     * Redacts {@code systemPrompt} and {@code userMessage} into one session,
     * calls {@code llmCall}, then rehydrates the response.
     */
    public String protect(String systemPrompt, String userMessage,
                          java.util.function.BiFunction<String, String, String> llmCall) {
        Objects.requireNonNull(llmCall, "llmCall");
        if (!isActive()) {
            return llmCall.apply(systemPrompt, userMessage);
        }

        PiiRedactionSession session = new PiiRedactionSession();
        ACTIVE.set(session);
        try {
            String safeSystem = systemPrompt;
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                safeSystem = redactor.redact(systemPrompt, config.getLevel(), session).redactedText();
            }
            String safeUser = userMessage;
            if (userMessage != null) {
                safeUser = redactor.redact(userMessage, config.getLevel(), session).redactedText();
            }
            if (!session.isEmpty()) {
                log.info("[PiiInterceptor] Outbound redaction typeCounts={}", session.typeCounts());
            }
            String response = llmCall.apply(safeSystem, safeUser);
            return rehydrator.rehydrate(response, session);
        } finally {
            ACTIVE.remove();
        }
    }

    /** Redact into a new session (caller owns rehydration). */
    public PiiRedactionResult redact(String text) {
        PiiRedactionSession session = new PiiRedactionSession();
        if (!isActive()) {
            return new PiiRedactionResult(text, text == null ? "" : text, java.util.List.of(), session);
        }
        return redactor.redact(text, config.getLevel(), session);
    }

    public String rehydrate(String text, PiiRedactionSession session) {
        if (!isActive() || session == null) {
            return text;
        }
        return rehydrator.rehydrate(text, session);
    }

    /**
     * When a protect() call is active on this thread, redacts into that session.
     * Otherwise returns {@code text} unchanged (or redacts into a throwaway session
     * only when {@code createIfAbsent} is true — not used by default).
     */
    public String redactUsingActiveSession(String text) {
        if (!isActive() || text == null) {
            return text;
        }
        PiiRedactionSession session = ACTIVE.get();
        if (session == null) {
            return text;
        }
        return redactor.redact(text, config.getLevel(), session).redactedText();
    }

    /** Bind a session for the current thread (used by AgenticChatGraph). */
    public void beginSession(PiiRedactionSession session) {
        ACTIVE.set(Objects.requireNonNull(session, "session"));
    }

    public void endSession() {
        ACTIVE.remove();
    }

    public PiiRedactionSession activeSession() {
        return ACTIVE.get();
    }
}
