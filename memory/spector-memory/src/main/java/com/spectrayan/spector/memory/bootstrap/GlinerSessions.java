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
package com.spectrayan.spector.memory.bootstrap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.config.model.TagExtractorMode;
import com.spectrayan.spector.config.properties.ExtractionProperties;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.provider.extraction.SpanExtractor;
import com.spectrayan.spector.provider.extraction.SpanExtractorFactory;
import com.spectrayan.spector.provider.extraction.SpanModelSpec;

/**
 * Opens one GLiNER session when tags or entities ask for it, and stores it on the builder.
 */
public final class GlinerSessions {

    private GlinerSessions() {
    }

    /**
     * Opens the session when {@code tag-extractor=gliner} or {@code extraction-mode=gliner}
     * and the builder does not already supply that extractor. A missing model fails startup.
     *
     * @param builder memory builder
     */
    public static void prepare(SpectorMemoryBuilder builder) {
        if (builder.spanExtractor() != null) {
            return;
        }
        var memory = builder.properties() != null ? builder.properties().memory() : null;
        boolean tags = memory != null && memory.getTagExtractor() == TagExtractorMode.GLINER
                && builder.tagExtractor() == null;
        String mode = memory != null && memory.getEntity() != null ? memory.getEntity().getExtractionMode() : null;
        boolean entities = mode != null && mode.trim().equalsIgnoreCase("gliner") && builder.entityExtractor() == null;
        if (!tags && !entities) {
            return;
        }
        ExtractionProperties extraction = builder.properties().provider() != null
                ? builder.properties().provider().getExtraction()
                : new ExtractionProperties();
        builder.spanExtractor(open(extraction));
    }

    /**
     * @param extraction provider extraction settings
     * @return an open session
     */
    public static SpanExtractor open(ExtractionProperties extraction) {
        ExtractionProperties props = extraction != null ? extraction : new ExtractionProperties();
        String type = props.getType() == null ? "" : props.getType().trim().toLowerCase(Locale.ROOT);
        if (!type.isEmpty() && !type.equals("gliner")) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "type",
                    "'" + props.getType() + "' is not gliner");
        }
        if (props.getRelationModelPath() != null && !props.getRelationModelPath().isBlank()) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "relation-model-path",
                    "a separate relation model is not loaded; relations are phrase links between spans");
        }
        List<String> labels = labels(props.getLabels());
        SpanModelSpec spec = new SpanModelSpec(
                path(props.getModelPath()),
                path(props.getTokenizerPath()),
                path(props.getConfigPath()),
                props.getEntityThreshold(),
                props.getIntraOpThreads(),
                props.getMaxWidth(),
                props.getMaxTokens(),
                labels);
        SpanExtractorFactory factory = null;
        for (SpanExtractorFactory candidate : ServiceLoader.load(SpanExtractorFactory.class)) {
            if ("gliner".equals(candidate.name())) {
                factory = candidate;
                break;
            }
        }
        if (factory == null) {
            throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "gliner",
                    "no span extractor is on the classpath");
        }
        return factory.open(spec);
    }

    /**
     * @param raw comma-separated labels
     * @return trimmed labels, in order
     */
    public static List<String> labels(String raw) {
        List<String> labels = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return labels;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                labels.add(trimmed);
            }
        }
        return labels;
    }

    private static Path path(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value.trim());
    }
}
