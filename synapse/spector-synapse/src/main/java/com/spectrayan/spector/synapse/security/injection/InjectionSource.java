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
package com.spectrayan.spector.synapse.security.injection;

/**
 * Origin of content scanned by {@link PromptShield}.
 */
public enum InjectionSource {
    /** Direct user message before cognitive / agentic graph entry. */
    USER_INPUT,
    /** Tool / MCP execution output before re-injection into LLM context. */
    TOOL_OUTPUT,
    /** Retrieved memory / RAG document before inclusion in context. */
    DOCUMENT
}
