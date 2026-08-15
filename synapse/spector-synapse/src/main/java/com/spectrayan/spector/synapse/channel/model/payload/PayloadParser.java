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
package com.spectrayan.spector.synapse.channel.model.payload;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility for parsing and converting native channel payloads into strongly typed DTOs.
 */
public final class PayloadParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PayloadParser() {}

    /**
     * Converts a raw object (Map, JSON string, byte[], or existing instance) into the target typed class.
     */
    public static <T> T parse(Object raw, Class<T> targetClass) {
        if (raw == null) {
            return null;
        }
        if (targetClass.isInstance(raw)) {
            return targetClass.cast(raw);
        }
        if (raw instanceof String str) {
            try {
                return MAPPER.readValue(str, targetClass);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse JSON payload as " + targetClass.getSimpleName(), e);
            }
        }
        if (raw instanceof byte[] bytes) {
            try {
                return MAPPER.readValue(bytes, targetClass);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse byte[] payload as " + targetClass.getSimpleName(), e);
            }
        }
        try {
            return MAPPER.convertValue(raw, targetClass);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert object to " + targetClass.getSimpleName(), e);
        }
    }
}
