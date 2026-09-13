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
package com.spectrayan.spector.synapse.security.pii;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core PII detection engine: regex patterns from
 * {@code /security/pii-patterns.yml} plus lightweight person-name heuristics.
 *
 * <p>Person-name detection is a <strong>heuristic stub</strong>, not a full NER
 * model — no heavy ML dependency is pulled. Extension point:
 * override or replace {@link #detectPersonNames(String, PiiLevel)} with a real
 * NER backend while keeping this public API stable.</p>
 *
 * <p>Fail-safe on missing/invalid YAML: empty pattern list (person heuristics
 * still run when the level allows). Operators see an ERROR log.</p>
 */
public final class PiiDetector {

    private static final Logger log = LoggerFactory.getLogger(PiiDetector.class);

    static final String RESOURCE_PATH = "/security/pii-patterns.yml";

    private static final Pattern HONORIFIC_NAME = Pattern.compile(
            "\\b(?:Mr|Mrs|Ms|Miss|Dr|Prof)\\.?\\s+[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?\\b");

    /** STRICT CapWord First Last (optional middle). Avoids single-token false hits. */
    private static final Pattern CAP_WORD_NAME = Pattern.compile(
            "\\b([A-Z][a-z]{1,30})\\s+([A-Z][a-z]{1,30})(?:\\s+([A-Z][a-z]{1,30}))?\\b");

    private static final java.util.Set<String> NAME_STOPWORDS = java.util.Set.of(
            "The", "This", "That", "These", "Those", "What", "When", "Where", "Which",
            "Who", "Whom", "Whose", "Why", "How", "Please", "Thanks", "Hello", "Dear",
            "From", "With", "Your", "Our", "Their", "His", "Her", "Its", "And", "But",
            "For", "Not", "You", "Are", "Was", "Were", "Been", "Being", "Have", "Has",
            "Had", "Will", "Would", "Could", "Should", "Might", "Must", "Shall",
            "Can", "Need", "Dare", "Ought", "Used", "Call", "Email", "Phone", "Address",
            "Street", "Avenue", "Road", "Drive", "Lane", "Court", "Monday", "Tuesday",
            "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "January", "February",
            "March", "April", "May", "June", "July", "August", "September", "October",
            "November", "December", "United", "States", "New", "York", "Los", "Angeles",
            "San", "Francisco", "South", "North", "East", "West", "Invoice", "About"
    );

    private static final List<PiiPattern> CACHED = loadClasspath();

    private final List<PiiPattern> patterns;

    /** Production constructor: classpath-cached YAML patterns. */
    public PiiDetector() {
        this(CACHED);
    }

    /** Visible for tests — inject patterns without re-reading the classpath. */
    PiiDetector(List<PiiPattern> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    List<PiiPattern> patterns() {
        return patterns;
    }

    /**
     * Detects PII spans at the given sensitivity level.
     *
     * @param text  content to scan (null/blank → empty)
     * @param level sensitivity gate
     * @return non-overlapping matches sorted by start index
     */
    public List<PiiMatch> detect(String text, PiiLevel level) {
        if (text == null || text.isBlank() || level == null) {
            return List.of();
        }

        List<PiiMatch> raw = new ArrayList<>();
        for (PiiPattern pp : patterns) {
            if (!pp.appliesAt(level)) {
                continue;
            }
            Matcher m = pp.pattern().matcher(text);
            while (m.find()) {
                String value = m.group();
                if (pp.type() == PiiType.CREDIT_CARD && !passesLuhn(value)) {
                    continue;
                }
                if (pp.type() == PiiType.PHONE && !looksLikePhone(value)) {
                    continue;
                }
                raw.add(new PiiMatch(pp.type(), value, m.start(), m.end()));
            }
        }

        raw.addAll(detectPersonNames(text, level));

        List<PiiMatch> resolved = resolveOverlaps(raw);
        if (!resolved.isEmpty()) {
            Map<PiiType, Integer> counts = new EnumMap<>(PiiType.class);
            for (PiiMatch match : resolved) {
                counts.merge(match.type(), 1, Integer::sum);
            }
            // Log type + count only — never actual PII values
            log.info("[PiiDetector] Detected PII types={} total={}", counts, resolved.size());
        }
        return resolved;
    }

    /**
     * Lightweight person-name heuristic (NER stub / extension point).
     *
     * <ul>
     *   <li>MODERATE+ — honorific + name ({@code Mr. John Smith})</li>
     *   <li>STRICT — also CapWord First Last pairs with stopword filtering</li>
     * </ul>
     */
    List<PiiMatch> detectPersonNames(String text, PiiLevel level) {
        if (level == null || !level.includes(PiiLevel.MODERATE)) {
            return List.of();
        }
        List<PiiMatch> names = new ArrayList<>();

        Matcher honorific = HONORIFIC_NAME.matcher(text);
        while (honorific.find()) {
            names.add(new PiiMatch(PiiType.PERSON, honorific.group(), honorific.start(), honorific.end()));
        }

        if (level.includes(PiiLevel.STRICT)) {
            Matcher caps = CAP_WORD_NAME.matcher(text);
            int searchFrom = 0;
            while (caps.find(searchFrom)) {
                String first = caps.group(1);
                String second = caps.group(2);
                if (NAME_STOPWORDS.contains(first)) {
                    // Advance past the stopword so "Call John Smith" can still yield "John Smith"
                    searchFrom = caps.start(1) + first.length();
                    continue;
                }
                if (NAME_STOPWORDS.contains(second)) {
                    searchFrom = caps.end();
                    continue;
                }
                names.add(new PiiMatch(PiiType.PERSON, caps.group(), caps.start(), caps.end()));
                searchFrom = caps.end();
            }
        }
        return names;
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

    /** Luhn check on digit-only form of a candidate card number. */
    static boolean passesLuhn(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    /** Reject short numeric fragments that aren't plausible phone numbers. */
    static boolean looksLikePhone(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.length() >= 10 && digits.length() <= 15;
    }

    private static List<PiiPattern> loadClasspath() {
        try (InputStream in = PiiDetector.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                log.error("[PiiDetector] Missing classpath resource {}; fail-safe: empty patterns",
                        RESOURCE_PATH);
                return List.of();
            }
            return parse(in);
        } catch (IOException ex) {
            log.error("[PiiDetector] Failed to read {}; fail-safe: empty patterns", RESOURCE_PATH, ex);
            return List.of();
        }
    }

    /**
     * Parses YAML of the form {@code patterns: [{id, type, minLevel, regex}, ...]}.
     * Invalid documents fail-safe to an empty list.
     */
    @SuppressWarnings("unchecked")
    static List<PiiPattern> parse(InputStream in) {
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object loaded = yaml.load(in);
            if (!(loaded instanceof Map<?, ?> map)) {
                log.error("[PiiDetector] YAML root must be a mapping; fail-safe: empty patterns");
                return List.of();
            }
            Object rawPatterns = map.get("patterns");
            if (!(rawPatterns instanceof List<?> list)) {
                log.error("[PiiDetector] 'patterns' must be a list; fail-safe: empty patterns");
                return List.of();
            }

            List<PiiPattern> compiled = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                String id = stringVal(entry.get("id"));
                String typeStr = stringVal(entry.get("type"));
                String levelStr = stringVal(entry.get("minLevel"));
                String regex = stringVal(entry.get("regex"));
                if (id == null || typeStr == null || levelStr == null || regex == null) {
                    log.warn("[PiiDetector] Skipping incomplete pattern entry");
                    continue;
                }
                try {
                    PiiType type = PiiType.valueOf(typeStr.toUpperCase(Locale.ROOT));
                    PiiLevel minLevel = PiiLevel.valueOf(levelStr.toUpperCase(Locale.ROOT));
                    Pattern pattern = Pattern.compile(regex);
                    compiled.add(new PiiPattern(id, type, pattern, minLevel));
                } catch (RuntimeException ex) {
                    log.warn("[PiiDetector] Skipping invalid pattern id={}: {}", id, ex.getMessage());
                }
            }
            log.info("[PiiDetector] Loaded {} patterns from {}", compiled.size(), RESOURCE_PATH);
            return List.copyOf(compiled);
        } catch (RuntimeException ex) {
            log.error("[PiiDetector] Invalid YAML {}; fail-safe: empty patterns", RESOURCE_PATH, ex);
            return List.of();
        }
    }

    private static String stringVal(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().strip();
        return s.isEmpty() ? null : s;
    }
}
