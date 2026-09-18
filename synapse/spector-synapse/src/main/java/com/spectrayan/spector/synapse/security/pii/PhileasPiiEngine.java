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
package com.spectrayan.spector.synapse.security.pii;

import ai.philterd.phileas.PhileasConfiguration;
import ai.philterd.phileas.model.filtering.FilterType;
import ai.philterd.phileas.model.filtering.Span;
import ai.philterd.phileas.model.filtering.TextFilterResult;
import ai.philterd.phileas.policy.Policy;
import ai.philterd.phileas.policy.PolicyCompilationException;
import ai.philterd.phileas.services.context.DefaultContextService;
import ai.philterd.phileas.services.disambiguation.vector.InMemoryVectorService;
import ai.philterd.phileas.services.filters.filtering.PlainTextFilterService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Internal Phileas adapter. Loads classpath PhiSQL policies once, runs identifier
 * filters in-process, and maps spans to Spector {@link PiiMatch} values.
 *
 * <p>Phileas types stay package-private — never appear on Synapse public APIs.</p>
 *
 * <p>No remote Ph-Eye: policies ship identifier filters only. Optional local ONNX
 * may be wired later behind explicit config.</p>
 */
final class PhileasPiiEngine {

    private static final Logger log = LoggerFactory.getLogger(PhileasPiiEngine.class);

    static final String RELAXED_POLICY = "/security/pii-policy-relaxed.phisql";
    static final String MODERATE_POLICY = "/security/pii-policy-moderate.phisql";
    static final String STRICT_POLICY = "/security/pii-policy-strict.phisql";

    private final PlainTextFilterService filterService;
    private final Map<PiiLevel, Policy> policies;

    PhileasPiiEngine() {
        this(createFilterService(), loadAllPolicies());
    }

    /** Visible for tests. */
    PhileasPiiEngine(PlainTextFilterService filterService, Map<PiiLevel, Policy> policies) {
        this.filterService = Objects.requireNonNull(filterService, "filterService");
        this.policies = Map.copyOf(Objects.requireNonNull(policies, "policies"));
        for (PiiLevel level : PiiLevel.values()) {
            if (!this.policies.containsKey(level)) {
                throw new IllegalStateException(
                        "[PhileasPiiEngine] Missing policy for level " + level + " (fail-closed)");
            }
        }
    }

    Map<PiiLevel, Policy> policies() {
        return policies;
    }

    List<PiiMatch> detect(String text, PiiLevel level, String contextId) {
        Objects.requireNonNull(level, "level");
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Policy policy = policies.get(level);
        if (policy == null) {
            throw new IllegalStateException(
                    "[PhileasPiiEngine] No policy cached for " + level + " (fail-closed)");
        }
        String context = (contextId == null || contextId.isBlank()) ? "spector" : contextId;
        try {
            TextFilterResult result = filterService.filter(policy, context, text);
            List<Span> spans = result.getExplanation() != null
                    ? result.getExplanation().appliedSpans()
                    : List.of();
            return toMatches(text, spans);
        } catch (Exception ex) {
            // Fail closed at the LLM boundary: do not silently pass unredacted text.
            throw new IllegalStateException(
                    "[PhileasPiiEngine] PII filter failed (fail-closed): " + ex.getMessage(), ex);
        }
    }

    private static List<PiiMatch> toMatches(String text, List<Span> spans) {
        if (spans == null || spans.isEmpty()) {
            return List.of();
        }
        List<PiiMatch> raw = new ArrayList<>(spans.size());
        for (Span span : spans) {
            PiiType type = mapType(span.getFilterType());
            if (type == null) {
                continue;
            }
            int start = span.getCharacterStart();
            int end = span.getCharacterEnd();
            if (start < 0 || end > text.length() || start >= end) {
                continue;
            }
            String value = span.getText();
            if (value == null || value.isEmpty()) {
                value = text.substring(start, end);
            }
            raw.add(new PiiMatch(type, value, start, end));
        }
        return resolveOverlaps(raw);
    }

    /**
     * Maps Phileas filter types onto Spector's public {@link PiiType}. Unknown /
     * NER-only types (Ph-Eye, person, first/surname) are dropped — identifier
     * filters only by default.
     */
    static PiiType mapType(FilterType filterType) {
        if (filterType == null) {
            return null;
        }
        return switch (filterType) {
            case EMAIL_ADDRESS -> PiiType.EMAIL;
            case PHONE_NUMBER, PHONE_NUMBER_EXTENSION -> PiiType.PHONE;
            case SSN -> PiiType.SSN;
            case CREDIT_CARD -> PiiType.CREDIT_CARD;
            case IP_ADDRESS -> PiiType.IP_ADDRESS;
            case STREET_ADDRESS, ZIP_CODE -> PiiType.ADDRESS;
            case PERSON, FIRST_NAME, SURNAME, PH_EYE -> null;
            default -> null;
        };
    }

    private static List<PiiMatch> resolveOverlaps(List<PiiMatch> raw) {
        if (raw.isEmpty()) {
            return List.of();
        }
        List<PiiMatch> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator
                .comparingInt(PiiMatch::start)
                .thenComparing((a, b) -> Integer.compare(b.length(), a.length())));
        List<PiiMatch> kept = new ArrayList<>();
        int lastEnd = -1;
        for (PiiMatch m : sorted) {
            if (m.start() < lastEnd) {
                continue;
            }
            kept.add(m);
            lastEnd = m.end();
        }
        return List.copyOf(kept);
    }

    static PlainTextFilterService createFilterService() {
        Properties props = new Properties();
        // Identifier-only policies; no Ph-Eye HTTP client needed (null = default unused).
        return new PlainTextFilterService(
                new PhileasConfiguration(props),
                new DefaultContextService(),
                new InMemoryVectorService(),
                null);
    }

    static Map<PiiLevel, Policy> loadAllPolicies() {
        EnumMap<PiiLevel, Policy> map = new EnumMap<>(PiiLevel.class);
        map.put(PiiLevel.RELAXED, loadPolicy(RELAXED_POLICY));
        map.put(PiiLevel.MODERATE, loadPolicy(MODERATE_POLICY));
        map.put(PiiLevel.STRICT, loadPolicy(STRICT_POLICY));
        log.info("[PhileasPiiEngine] Loaded Phileas policies for levels {}", map.keySet());
        return map;
    }

    static Policy loadPolicy(String resourcePath) {
        try (InputStream in = PhileasPiiEngine.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "[PhileasPiiEngine] Missing classpath policy " + resourcePath
                                + " (fail-closed)");
            }
            String phisql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Policy.fromPhiSQL(phisql);
        } catch (PolicyCompilationException ex) {
            throw new IllegalStateException(
                    "[PhileasPiiEngine] Invalid PhiSQL policy " + resourcePath
                            + " (fail-closed): " + ex.getMessage(),
                    ex);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "[PhileasPiiEngine] Failed to read policy " + resourcePath
                            + " (fail-closed)",
                    ex);
        }
    }
}
