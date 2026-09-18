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
package com.spectrayan.spector.synapse.bridge;

/**
 * Exception thrown by {@link LlmBridge} when LLM generation fails.
 *
 * <p>Wraps the underlying LangChain4j/Ollama exception to provide
 * structured error propagation instead of silently returning error strings.</p>
 */
public class LlmBridgeException extends RuntimeException {

    public LlmBridgeException(String message) {
        super(message);
    }

    public LlmBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
