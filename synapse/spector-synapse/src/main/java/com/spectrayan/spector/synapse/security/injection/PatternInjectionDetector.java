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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Fast regex-based detector for known prompt-injection / jailbreak patterns.
 *
 * <p>Designed for the &lt; 50ms P99 fast path: patterns are compiled once and
 * matched case-insensitively against normalized text.</p>
 */
public final class PatternInjectionDetector {

    private static final Logger log = LoggerFactory.getLogger(PatternInjectionDetector.class);

    private record NamedPattern(String id, Pattern pattern, double score) {}

    private static final List<NamedPattern> PATTERNS = List.of(
            named("ignore-previous-instructions",
                    "(?i)\\b(ignore|disregard|forget)\\b.{0,40}\\b(previous|prior|above|all)\\b.{0,40}\\b(instructions?|prompts?|rules?|guidelines?)\\b",
                    0.95),
            named("override-system-prompt",
                    "(?i)\\b(override|replace|reset)\\b.{0,40}\\b(system\\s*prompt|system\\s*message|your\\s+instructions?)\\b",
                    0.93),
            named("reveal-system-prompt",
                    "(?i)\\b(reveal|show|print|dump|repeat)\\b.{0,40}\\b(system\\s*prompt|hidden\\s*prompt|initial\\s*instructions?)\\b",
                    0.9),
            named("you-are-now",
                    "(?i)\\byou\\s+are\\s+now\\b.{0,60}\\b(unrestricted|jailbroken|DAN|evil|no\\s+rules?)\\b",
                    0.92),
            named("dan-jailbreak",
                    "(?i)\\b(do\\s+anything\\s+now|\\bDAN\\b\\s*mode|jailbreak\\s+mode)\\b",
                    0.95),
            named("pretend-no-restrictions",
                    "(?i)\\b(pretend|act\\s+as\\s+if|role[- ]?play)\\b.{0,60}\\b(no\\s+(restrictions?|rules?|limits?)|unfiltered|uncensored)\\b",
                    0.88),
            named("from-now-on",
                    "(?i)\\bfrom\\s+now\\s+on\\b.{0,40}\\b(you\\s+(will|must|shall)|ignore)\\b",
                    0.85),
            named("developer-mode",
                    "(?i)\\b(enable|enter|activate)\\b.{0,30}\\b(developer|god|sudo)\\s*mode\\b",
                    0.87),
            named("new-instructions-marker",
                    "(?i)\\b(new\\s+system\\s+prompt|begin\\s+system\\s+prompt|\\[SYSTEM\\]|<<\\s*SYS\\s*>>)\\b",
                    0.9),
            named("indirect-ai-must",
                    "(?i)\\b(important|attention|note)\\s*[:\\-]\\s*.{0,40}\\b(ai|assistant|llm|model)\\b.{0,40}\\b(must|should|ignore|disregard)\\b",
                    0.8)
    );

    private static NamedPattern named(String id, String regex, double score) {
        return new NamedPattern(id, Pattern.compile(regex), score);
    }

    /**
     * Scans {@code text} for known injection patterns.
     *
     * @param text   content to scan (null/blank → clean)
     * @param source origin used only for typing (USER_INPUT → DIRECT, else INDIRECT)
     * @return highest-scoring hit, or clean result
     */
    public InjectionResult detect(String text, InjectionSource source) {
        long start = System.nanoTime();
        if (text == null || text.isBlank()) {
            return InjectionResult.clean(source, System.nanoTime() - start);
        }

        NamedPattern best = null;
        for (NamedPattern np : PATTERNS) {
            if (np.pattern().matcher(text).find()) {
                if (best == null || np.score() > best.score()) {
                    best = np;
                }
            }
        }

        long latency = System.nanoTime() - start;
        if (best == null) {
            return InjectionResult.clean(source, latency);
        }

        InjectionType type = source == InjectionSource.USER_INPUT
                ? InjectionType.DIRECT
                : InjectionType.INDIRECT;

        log.debug("[PatternInjectionDetector] hit id={} score={} source={} latencyUs={}",
                best.id(), best.score(), source, latency / 1_000);

        return InjectionResult.hit(
                type,
                best.score(),
                best.id(),
                "Matched injection pattern: " + best.id(),
                source,
                false, // blocked decided by PromptShield mode
                latency);
    }
}
