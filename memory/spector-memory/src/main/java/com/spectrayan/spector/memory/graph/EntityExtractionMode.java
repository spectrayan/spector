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
package com.spectrayan.spector.memory.graph;

/**
 * Configuration mode for entity extraction during memory ingestion.
 *
 * <p>Controls whether entities are extracted from memory text during ingestion
 * and which extraction strategy is used.</p>
 */
public enum EntityExtractionMode {
    /** No entity extraction (default). Entity graph features are disabled. */
    NONE,

    /** LLM-powered extraction via LlmProvider. */
    LLM,

    /** Dictionary-based extraction using existing EntityDirectory and TemporalKnowledgeGraph. */
    DICTIONARY,

    /** Custom EntityExtractor provided via Builder. */
    CUSTOM
}
