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
package com.spectrayan.spector.memory.session;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A per-namespace session registry that maps TSID session string identifiers
 * to integer IDs for efficient internal processing.
 */
public final class SessionRegistry {
    private final ConcurrentHashMap<String, Integer> forward = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> reverse = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Get or assign an int ID for a TSID session string.
     *
     * @param tsidSessionId the TSID session string
     * @return the mapped int ID, or 0 if tsidSessionId is null
     */
    public int resolve(String tsidSessionId) {
        if (tsidSessionId == null) {
            return 0;
        }
        return forward.computeIfAbsent(tsidSessionId, key -> {
            int id = nextId.getAndIncrement();
            reverse.put(id, key);
            return id;
        });
    }

    /**
     * Reverse lookup: int ID → TSID string.
     *
     * @param sessionIntId the integer session ID
     * @return the TSID string, or null if unknown
     */
    public String reverseLookup(int sessionIntId) {
        return reverse.get(sessionIntId);
    }

    /**
     * Returns the number of registered sessions.
     *
     * @return the size of the registry
     */
    public int size() {
        return forward.size();
    }
}
