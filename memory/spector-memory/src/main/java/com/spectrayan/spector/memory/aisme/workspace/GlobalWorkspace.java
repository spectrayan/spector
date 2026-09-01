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
package com.spectrayan.spector.memory.aisme.workspace;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.model.CognitiveResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Limited-capacity Conscious Access Gateway & Global Workspace (Baars / Dehaene).
 *
 * <h3>Biological Analog: Global Neuronal Workspace Conscious Broadcast</h3>
 * <p>Implements the limited-capacity conscious bottleneck (~7 items), selecting the most
 * resonant, free-energy minimizing, and narrative-aligned memory representations for global broadcast.</p>
 */
public final class GlobalWorkspace {

    private static final Logger log = LoggerFactory.getLogger(GlobalWorkspace.class);
    private static final int DEFAULT_CAPACITY = 7;

    private final ReentrantLock lock = new ReentrantLock();
    private final int capacity;
    private AttentionSchema activeSchema;

    /**
     * Constructs a GlobalWorkspace with default conscious capacity (7 items).
     */
    public GlobalWorkspace() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Constructs a GlobalWorkspace with custom broadcast capacity.
     *
     * @param capacity maximum number of candidate memories admitted to conscious broadcast
     */
    public GlobalWorkspace(int capacity) {
        if (capacity < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Workspace capacity must be at least 1");
        }
        this.capacity = capacity;
        this.activeSchema = AttentionSchema.defaultSchema();
    }

    /**
     * Selects and broadcasts the top candidate memories into the conscious workspace bottleneck.
     *
     * @param candidates candidate memory items
     * @return pruned list of items admitted to global broadcast
     */
    public List<CognitiveResult> filterForBroadcast(List<CognitiveResult> candidates) {
        return filterForBroadcast(candidates, capacity);
    }

    /**
     * Selects and broadcasts the top candidate memories with an explicit capacity override.
     *
     * @param candidates candidate memory items
     * @param effectiveCapacity maximum number of items to admit
     * @return pruned list of items admitted to global broadcast
     */
    public List<CognitiveResult> filterForBroadcast(List<CognitiveResult> candidates, int effectiveCapacity) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<CognitiveResult> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(CognitiveResult::score).reversed());

        int limit = Math.min(sorted.size(), effectiveCapacity);
        List<CognitiveResult> broadcastList = new ArrayList<>(sorted.subList(0, limit));

        if (log.isTraceEnabled()) {
            log.trace("GlobalWorkspace admitted {} / {} candidates to conscious broadcast (capacity={})",
                    broadcastList.size(), candidates.size(), effectiveCapacity);
        }

        return broadcastList;
    }

    /**
     * Updates the active attention schema.
     *
     * @param schema new AttentionSchema
     */
    public void updateAttentionSchema(AttentionSchema schema) {
        if (schema == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "AttentionSchema must not be null");
        }
        lock.lock();
        try {
            this.activeSchema = schema;
        } finally {
            lock.unlock();
        }
    }

    public AttentionSchema activeSchema() {
        lock.lock();
        try {
            return activeSchema;
        } finally {
            lock.unlock();
        }
    }

    public int capacity() {
        return capacity;
    }
}
