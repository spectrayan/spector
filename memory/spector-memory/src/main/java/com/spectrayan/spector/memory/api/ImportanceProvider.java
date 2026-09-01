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
package com.spectrayan.spector.memory.api;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.*;
import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.model.ImportanceContext;
import com.spectrayan.spector.memory.model.ImportanceResult;

/**
 * Service Provider Interface (SPI) for determining the importance of a candidate memory.
 */
public interface ImportanceProvider {

    /**
     * Computes importance for a candidate memory.
     * <p>Implementations receive all available context and return a scored result
     * with an explainability breakdown. The core engine calls this during both
     * pre-flight estimation ({@code estimateImportance}) and actual ingestion
     * ({@code remember}).</p>
     *
     * @param context all inputs available at importance-scoring time
     * @return the scored result including breakdown for explainability
     */
    ImportanceResult score(ImportanceContext context);

    /**
     * Returns a baseline provider that always returns neutral importance (1.0).
     * <p>Use for tests, minimal configurations, and backward compatibility.</p>
     *
     * @return a baseline ImportanceProvider
     */
    static ImportanceProvider baseline() {
        return ctx -> ImportanceResult.baseline();
    }
}
