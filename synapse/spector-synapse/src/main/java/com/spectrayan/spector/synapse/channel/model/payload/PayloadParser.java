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
