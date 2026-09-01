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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;

/**
 * Exception thrown when cognitive pathway dispatch or relay processing aborts.
 *
 * <p>Uses {@link ErrorCode#MEMORY_PATHWAY_FAILED} ({@code SPE-310-017}).</p>
 *
 * @since 1.4.0
 */
public class SpectorPathwayException extends SpectorMemoryException {

    private final String details;

    public SpectorPathwayException(String details) {
        super(ErrorCode.MEMORY_PATHWAY_FAILED, details);
        this.details = details;
    }

    public SpectorPathwayException(String details, Throwable cause) {
        super(ErrorCode.MEMORY_PATHWAY_FAILED, cause, details);
        this.details = details;
    }

    public SpectorPathwayException(ErrorCode errorCode, String details) {
        super(errorCode, details);
        this.details = details;
    }

    public SpectorPathwayException(ErrorCode errorCode, Throwable cause, String details) {
        super(errorCode, cause, details);
        this.details = details;
    }

    public String getDetails() {
        return details;
    }
}
