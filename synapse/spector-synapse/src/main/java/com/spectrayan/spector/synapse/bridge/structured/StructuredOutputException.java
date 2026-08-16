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
package com.spectrayan.spector.synapse.bridge.structured;

/**
 * Exception thrown when LLM output cannot be parsed into the expected JSON structure or schema.
 */
public class StructuredOutputException extends RuntimeException {

    private final String rawOutput;
    private final String jsonSchema;

    public StructuredOutputException(String message) {
        this(message, null, null, null);
    }

    public StructuredOutputException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public StructuredOutputException(String message, String rawOutput, String jsonSchema, Throwable cause) {
        super(message, cause);
        this.rawOutput = rawOutput;
        this.jsonSchema = jsonSchema;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public String getJsonSchema() {
        return jsonSchema;
    }
}
