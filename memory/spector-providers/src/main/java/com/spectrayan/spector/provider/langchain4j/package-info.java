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
 * Shared LangChain4j adapters and helpers used by the concrete provider factories.
 *
 * <p>This package does not register a provider of its own. It bridges LangChain4j models
 * to the Spector SPI and applies common HTTP settings.</p>
 *
 * <ul>
 *   <li>{@link com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter} — wraps a
 *       LangChain4j {@code EmbeddingModel} as an embedding provider</li>
 *   <li>{@link com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter} — wraps a
 *       LangChain4j {@code ChatModel} as a text-generation provider</li>
 *   <li>{@link com.spectrayan.spector.provider.langchain4j.LangChain4jHelper} — resolves the HTTP
 *       client (proxy, mTLS) and custom headers from provider properties</li>
 * </ul>
 */
package com.spectrayan.spector.provider.langchain4j;
