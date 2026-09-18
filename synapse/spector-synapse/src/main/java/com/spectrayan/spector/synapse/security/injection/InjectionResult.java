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
