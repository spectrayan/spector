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
