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
 * Provides in-process ONNX embedding generation using LangChain4j embedding models.
 *
 * <p>This provider supports embeddings only; text generation is not provided. Vectors are
 * computed inside the JVM with no network calls or API key, from a model file path or a
 * pre-packaged LangChain4j model found on the classpath.</p>
 */
package com.spectrayan.spector.provider.onnx;
