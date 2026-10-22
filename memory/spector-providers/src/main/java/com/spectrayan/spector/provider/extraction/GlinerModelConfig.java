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
package com.spectrayan.spector.provider.extraction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;

/**
 * The fields a GLiNER span session needs from {@code gliner_config.json}.
 */
final class GlinerModelConfig {

    final String entToken;
    final String sepToken;
    final int maxWidth;
    final int maxLen;
    final int maxTypes;

    private GlinerModelConfig(String entToken, String sepToken, int maxWidth, int maxLen, int maxTypes) {
        this.entToken = entToken;
        this.sepToken = sepToken;
        this.maxWidth = maxWidth;
        this.maxLen = maxLen;
        this.maxTypes = maxTypes;
    }

    static GlinerModelConfig parse(String json, String source) {
        if (json == null || json.isBlank()) {
            throw invalid(source, "empty gliner config");
        }
        String modelType = stringField(json, "model_type");
        if (modelType != null && !modelType.equals("gliner")) {
            throw invalid(source, "model_type '" + modelType + "' is not gliner");
        }
        String splitter = stringField(json, "words_splitter_type");
        if (splitter != null && !splitter.equals("whitespace")) {
            throw invalid(source, "words_splitter_type '" + splitter + "' is not whitespace");
        }
        String ent = stringField(json, "ent_token");
        String sep = stringField(json, "sep_token");
        if (ent == null || sep == null) {
            throw invalid(source, "ent_token and sep_token are required");
        }
        int maxWidth = intField(json, "max_width", source);
        int maxLen = intField(json, "max_len", source);
        int maxTypes = intField(json, "max_types", 25, source);
        if (maxWidth <= 0 || maxLen <= 0) {
            throw invalid(source, "max_width and max_len must be positive");
        }
        return new GlinerModelConfig(ent, sep, maxWidth, maxLen, maxTypes);
    }

    private static String stringField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int intField(String json, String name, String source) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) {
            throw invalid(source, "missing " + name);
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            throw invalid(source, "invalid " + name);
        }
    }

    private static int intField(String json, String name, int fallback, String source) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            throw invalid(source, "invalid " + name);
        }
    }

    private static SpectorConfigException invalid(String source, String reason) {
        return new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, source, reason);
    }
}
