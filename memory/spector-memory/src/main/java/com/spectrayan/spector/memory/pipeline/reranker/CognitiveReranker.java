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
package com.spectrayan.spector.memory.pipeline.reranker;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;


/**
 * Handles late-stage multi-vector reranking using ColBERT SIMD acceleration.
 */
public class CognitiveReranker {

    private final ColBERTReranker colbertReranker;

    public CognitiveReranker(ColBERTReranker colbertReranker) {
        this.colbertReranker = colbertReranker;
    }

    public ColBERTReranker colbertReranker() {
        return colbertReranker;
    }
}
