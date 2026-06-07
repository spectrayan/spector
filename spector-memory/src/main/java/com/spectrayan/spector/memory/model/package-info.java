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

/**
 * Data model types for the Spector cognitive memory system.
 *
 * <p>This package contains all record and enum types that define the
 * public data contracts of the memory subsystem:</p>
 *
 * <h3>Records (immutable data carriers)</h3>
 * <ul>
 *   <li>{@link com.spectrayan.spector.memory.model.CognitiveResult} — a single recall result with score breakdown</li>
 *   <li>{@link com.spectrayan.spector.memory.model.ImportanceEstimate} — pre-ingestion importance computation</li>
 *   <li>{@link com.spectrayan.spector.memory.model.IngestionContext} — consolidated ingestion metadata (hints, entities, edges)</li>
 *   <li>{@link com.spectrayan.spector.memory.model.RecallOptions} — full recall configuration (topK, alpha, beta, filters, etc.)</li>
 *   <li>{@link com.spectrayan.spector.memory.model.RecallTrace} — step-by-step recall pipeline audit trail</li>
 *   <li>{@link com.spectrayan.spector.memory.model.ReflectReport} — sleep consolidation report</li>
 *   <li>{@link com.spectrayan.spector.memory.model.ScoreBreakdown} — detailed scoring component breakdown</li>
 *   <li>{@link com.spectrayan.spector.memory.model.WhyNotExplanation} — explainability for why a memory was filtered</li>
 * </ul>
 *
 * <h3>Enums (type-safe constants)</h3>
 * <ul>
 *   <li>{@link com.spectrayan.spector.memory.model.CognitiveProfile} — preset recall scoring profiles (BALANCED, DEBUGGING, HYPERFOCUS, etc.)</li>
 *   <li>{@link com.spectrayan.spector.memory.model.ConfidenceBand} — result confidence classification</li>
 *   <li>{@link com.spectrayan.spector.memory.model.MemoryPersistenceMode} — IN_MEMORY vs DISK storage</li>
 *   <li>{@link com.spectrayan.spector.memory.model.MemoryType} — memory tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)</li>
 *   <li>{@link com.spectrayan.spector.memory.model.RecallMode} — recall strategy selection</li>
 *   <li>{@link com.spectrayan.spector.memory.model.ScoringMode} — scoring algorithm variants</li>
 *   <li>{@link com.spectrayan.spector.memory.model.TextSearchMode} — text search strategy (BM25, SEMANTIC, HYBRID)</li>
 * </ul>
 */
package com.spectrayan.spector.memory.model;
