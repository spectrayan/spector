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
package com.spectrayan.spector.commons;

import java.util.Optional;

/**
 * Common number parsing utilities that safely handle exceptions.
 */
public final class ParseUtils {

    private ParseUtils() {}

    /**
     * Parses a string to long, returning the defaultValue if parsing fails or if value is null.
     *
     * @param value        the string value to parse
     * @param defaultValue the fallback value
     * @return the parsed long or the defaultValue
     */
    public static long parseLongOrDefault(String value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses a string to Integer, returning Optional.empty() if parsing fails or if value is null.
     *
     * @param value the string value to parse
     * @return an Optional containing the parsed integer, or empty
     */
    public static Optional<Integer> parseInteger(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses a string to Double, returning Optional.empty() if parsing fails or if value is null.
     *
     * @param value the string value to parse
     * @return an Optional containing the parsed double, or empty
     */
    public static Optional<Double> parseDouble(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
