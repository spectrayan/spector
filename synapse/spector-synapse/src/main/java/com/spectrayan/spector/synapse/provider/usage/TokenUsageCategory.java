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
package com.spectrayan.spector.synapse.provider.usage;

/**
 * Category of operation consuming LLM or embedding tokens.
 */
public enum TokenUsageCategory {
    /** Interactive or API-driven chat turn. */
    CHAT,
    /** Semantic search, vector recall, or indexing embedding. */
    EMBEDDING,
    /** Memory consolidation (e.g. sleep/REM synthesis or clustering). */
    CONSOLIDATION,
    /** Cognitive reflection or persona self-evaluation. */
    REFLECTION,
    /** Tool selection, argument generation, or tool evaluation. */
    TOOL_EXECUTION,
    /** Internal agent system, priming, or health operations. */
    SYSTEM
}
