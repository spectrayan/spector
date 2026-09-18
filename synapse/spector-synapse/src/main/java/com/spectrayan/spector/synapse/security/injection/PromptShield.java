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

import com.spectrayan.spector.synapse.security.config.SecurityProperties;
import com.spectrayan.spector.synapse.security.config.SecurityProperties.InjectionProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Main entry point for prompt-injection detection.
 *
 * <p>Runs the fast {@link PatternInjectionDetector} first, then optionally the
 * {@link ClassifierInjectionDetector} heuristic when the pattern path is clean.
 * Enforcement respects {@code spector.security.injection.enabled} and
 * {@code spector.security.injection.mode} ({@code BLOCK}/{@code WARN}/{@code OFF}).</p>
 */
public final class PromptShield {

    private static final Logger log = LoggerFactory.getLogger(PromptShield.class);

    private final InjectionProperties config;
    private final PatternInjectionDetector patternDetector;
    private final ClassifierInjectionDetector classifierDetector;

    public PromptShield(SecurityProperties securityProperties) {
        this(securityProperties != null ? securityProperties.getInjection() : new InjectionProperties(),
                new PatternInjectionDetector(),
                new ClassifierInjectionDetector());
    }

    public PromptShield(InjectionProperties config,
                        PatternInjectionDetector patternDetector,
                        ClassifierInjectionDetector classifierDetector) {
        this.config = Objects.requireNonNullElseGet(config, InjectionProperties::new);
        this.patternDetector = Objects.requireNonNull(patternDetector, "patternDetector");
        this.classifierDetector = Objects.requireNonNull(classifierDetector, "classifierDetector");
    }

    /** Convenience factory with default detectors and config. */
    public static PromptShield createDefault() {
        return new PromptShield(new SecurityProperties());
    }

    /**
     * Scans content and applies mode policy (block / warn / off).
     *
     * @param text   content to scan
     * @param source origin of the content
     * @return result (blocked=true only when mode is BLOCK and a hit was found)
     * @throws PromptInjectionException when mode is BLOCK and injection is detected
     */
    public InjectionResult inspect(String text, InjectionSource source) {
        long start = System.nanoTime();
        if (!config.isActive()) {
            return InjectionResult.clean(source, System.nanoTime() - start);
        }

        InjectionResult pattern = patternDetector.detect(text, source);
        InjectionResult raw = pattern.detected()
                ? pattern
                : classifierDetector.detect(text, source);

        if (!raw.detected()) {
            return raw;
        }

        boolean block = config.isBlock();
        InjectionResult result = InjectionResult.hit(
                raw.type(),
                raw.score(),
                raw.matchedPattern(),
                raw.message(),
                raw.source(),
                block,
                raw.latencyNanos() + (System.nanoTime() - start - raw.latencyNanos()));

        if (block) {
            log.warn("[PromptShield] BLOCKED {} injection pattern={} score={} source={} latencyUs={}",
                    result.type(), result.matchedPattern(), result.score(), source,
                    result.latencyNanos() / 1_000);
            throw new PromptInjectionException(result);
        }

        log.warn("[PromptShield] WARN {} injection pattern={} score={} source={} latencyUs={}",
                result.type(), result.matchedPattern(), result.score(), source,
                result.latencyNanos() / 1_000);
        return result;
    }

    /**
     * Scans without throwing; returns a result with {@code blocked} set when mode is BLOCK.
     * Useful for document filtering where callers prefer to drop a chunk rather than fail.
     */
    public InjectionResult evaluate(String text, InjectionSource source) {
        if (!config.isActive()) {
            return InjectionResult.clean(source, 0L);
        }
        try {
            return inspect(text, source);
        } catch (PromptInjectionException ex) {
            return ex.getResult();
        }
    }

    public InjectionProperties config() {
        return config;
    }
}
