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

import java.util.Collections;
import java.util.UUID;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Request-scoped map of redaction tokens ({@code [EMAIL_1]}) to original values.
 *
 * <p>Thread-safe via {@link ReentrantLock}. Original values live only in this
 * session and must never be logged.</p>
 */
public final class PiiRedactionSession {

    private final String contextId = UUID.randomUUID().toString();
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, String> tokenToOriginal = new LinkedHashMap<>();
    private final Map<String, String> originalToToken = new LinkedHashMap<>();
    private final EnumMap<PiiType, Integer> counters = new EnumMap<>(PiiType.class);
    private final EnumMap<PiiType, Integer> typeCounts = new EnumMap<>(PiiType.class);

    /**
     * Phileas filter context id for this session (stable for the request).
     * Spector tokens are still allocated here — not Phileas opaque replacements.
     */
    public String contextId() {
        return contextId;
    }

    /**
     * Returns an existing token for {@code original} if already mapped, otherwise
     * allocates {@code [TYPE_N]} and stores the mapping.
     */
    public String tokenize(PiiType type, String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }
        lock.lock();
        try {
            String existing = originalToToken.get(original);
            if (existing != null) {
                return existing;
            }
            int next = counters.getOrDefault(type, 0) + 1;
            counters.put(type, next);
            typeCounts.merge(type, 1, Integer::sum);
            String token = "[" + type.name() + "_" + next + "]";
            tokenToOriginal.put(token, original);
            originalToToken.put(original, token);
            return token;
        } finally {
            lock.unlock();
        }
    }

    public String lookup(String token) {
        lock.lock();
        try {
            return tokenToOriginal.get(token);
        } finally {
            lock.unlock();
        }
    }

    /** Immutable snapshot of token → original (for rehydration). */
    public Map<String, String> tokenMap() {
        lock.lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(tokenToOriginal));
        } finally {
            lock.unlock();
        }
    }

    /** Per-type detection counts (safe to log — no values). */
    public Map<PiiType, Integer> typeCounts() {
        lock.lock();
        try {
            return Collections.unmodifiableMap(new EnumMap<>(typeCounts));
        } finally {
            lock.unlock();
        }
    }

    public int totalCount() {
        lock.lock();
        try {
            return typeCounts.values().stream().mapToInt(Integer::intValue).sum();
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return tokenToOriginal.isEmpty();
        } finally {
            lock.unlock();
        }
    }
}
