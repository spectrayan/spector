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
/**
 * Spector Engine — Unified search engine facade, lifecycle management, and ingestion pipeline.
 *
 * <p>Provides a single entry-point API ({@code SpectorEngine}) for creating indexes,
 * ingesting documents, and executing searches. Manages the lifecycle of all
 * underlying resources (arenas, indexes, thread executors).</p>
 *
 * @deprecated This module is scheduled for removal. {@code spector-memory} is the flagship
 *     cognitive engine. Engine responsibilities will be migrated to memory and runtime.
 *     See <a href="https://github.com/spectrayan/spector/issues/285">GitHub #285</a>.
 */
@Deprecated(forRemoval = true)
package com.spectrayan.spector.engine;
