/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
