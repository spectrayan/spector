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
package com.spectrayan.spector.provider.embedding;

/**
 * Marker interface for in-process embedding providers that execute directly inside
 * the JVM process (e.g., via ONNX Runtime / Java FFM) without network I/O.
 *
 * <p>In-process embedding providers guarantee low single-digit millisecond latency
 * and are safe for synchronous query transduction on the fast Recall Plane.</p>
 */
public interface InProcessEmbeddingProvider extends EmbeddingProvider {

    /**
     * Returns true indicating this provider executes in-process with zero network I/O.
     */
    default boolean isInProcess() {
        return true;
    }

    /**
     * Returns the execution provider backend (e.g., "CPU", "DIRECTML", "CUDA").
     */
    default String executionBackend() {
        return "CPU";
    }
}
