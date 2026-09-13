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
package com.spectrayan.spector.synapse.security.injection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Lightweight heuristic classifier for prompt injection.
 *
 * <p>This is a stub-friendly second layer: it scores suspicious keyword density
 * without calling an external LLM. A future revision may swap in a small local
 * classifier while preserving this API.</p>
 */
public final class ClassifierInjectionDetector {

    private static final Logger log = LoggerFactory.getLogger(ClassifierInjectionDetector.class);

    private static final String[] SIGNALS = {
            "ignore previous",
            "ignore all instructions",
            "system prompt",
            "jailbreak",
            "developer mode",
            "do anything now",
            "no restrictions",
            "override instructions",
            "reveal your prompt",
            "unfiltered responses"
    };

    private static final double THRESHOLD = 0.55;

    /**
     * Heuristic score over keyword hits. Returns a detection only when score ≥ threshold.
     */
    public InjectionResult detect(String text, InjectionSource source) {
        long start = System.nanoTime();
        if (text == null || text.isBlank()) {
            return InjectionResult.clean(source, System.nanoTime() - start);
        }

        String lower = text.toLowerCase(Locale.ROOT);
        int hits = 0;
        String matched = null;
        for (String signal : SIGNALS) {
            if (lower.contains(signal)) {
                hits++;
                if (matched == null) {
                    matched = signal;
                }
            }
        }

        double score = Math.min(1.0, hits / 3.0);
        long latency = System.nanoTime() - start;

        if (score < THRESHOLD) {
            return InjectionResult.clean(source, latency);
        }

        InjectionType type = source == InjectionSource.USER_INPUT
                ? InjectionType.DIRECT
                : InjectionType.INDIRECT;

        log.debug("[ClassifierInjectionDetector] hit signals={} score={} source={}",
                hits, score, source);

        return InjectionResult.hit(
                type,
                score,
                "heuristic:" + matched,
                "Heuristic classifier flagged injection signals",
                source,
                false,
                latency);
    }
}
