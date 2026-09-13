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

/**
 * Outcome of a prompt-injection scan.
 *
 * @param detected       whether an injection pattern/heuristic fired
 * @param blocked        whether the shield will reject this content (BLOCK mode)
 * @param type           direct vs indirect vs none
 * @param score          detection confidence in {@code [0.0, 1.0]}
 * @param matchedPattern human-readable pattern id or heuristic label
 * @param message        short explanation suitable for logs / errors
 * @param source         content origin
 * @param latencyNanos   scan duration for fast-path telemetry
 */
public record InjectionResult(
        boolean detected,
        boolean blocked,
        InjectionType type,
        double score,
        String matchedPattern,
        String message,
        InjectionSource source,
        long latencyNanos
) {
    public static InjectionResult clean(InjectionSource source, long latencyNanos) {
        return new InjectionResult(
                false, false, InjectionType.NONE, 0.0, null, "clean", source, latencyNanos);
    }

    public static InjectionResult hit(
            InjectionType type,
            double score,
            String matchedPattern,
            String message,
            InjectionSource source,
            boolean blocked,
            long latencyNanos) {
        return new InjectionResult(
                true, blocked, type, score, matchedPattern, message, source, latencyNanos);
    }
}
