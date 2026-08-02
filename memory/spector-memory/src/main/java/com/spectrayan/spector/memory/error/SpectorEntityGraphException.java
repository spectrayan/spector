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
package com.spectrayan.spector.memory.error;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Exception thrown when an entity graph operation fails.
 *
 * <p>Covers entity addition, relation linking, entity lookup,
 * memory linking, and graph traversal ({@code SPE-310-008}). Defaults to
 * {@link ErrorCode#GRAPH_ENTITY_FAILED}, but can carry a more specific code
 * such as {@link ErrorCode#CAPACITY_EXCEEDED} for adjacency exhaustion.</p>
 *
 * @see ErrorCode#GRAPH_ENTITY_FAILED
 * @see ErrorCode#CAPACITY_EXCEEDED
 */
public class SpectorEntityGraphException extends SpectorGraphException {

    private final String operation;

    public SpectorEntityGraphException(String operation) {
        super(ErrorCode.GRAPH_ENTITY_FAILED, operation);
        this.operation = operation;
    }

    public SpectorEntityGraphException(String operation, Throwable cause) {
        super(ErrorCode.GRAPH_ENTITY_FAILED, cause, operation);
        this.operation = operation;
    }

    /**
     * Creates an entity graph exception with a specific {@link ErrorCode}.
     *
     * <p>Use this when the failure maps to a more precise code than the default
     * {@link ErrorCode#GRAPH_ENTITY_FAILED} (for example
     * {@link ErrorCode#CAPACITY_EXCEEDED} when an adjacency segment is
     * exhausted). The {@code operation} is retained for {@link #getOperation()};
     * the {@code messageArgs} are formatted into the code's message template.</p>
     *
     * @param errorCode   the specific error code to surface
     * @param operation   the entity graph operation that failed (for diagnostics)
     * @param messageArgs arguments substituted into {@code errorCode}'s template
     */
    public SpectorEntityGraphException(ErrorCode errorCode, String operation, Object... messageArgs) {
        super(errorCode, messageArgs);
        this.operation = operation;
    }

    /** Returns the entity graph operation that failed. */
    public String getOperation() {
        return operation;
    }
}
