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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight heuristic classifier for prompt injection.
 *
 * <p>This is a stub-friendly second layer: it scores suspicious keyword density
 * without calling an external LLM. A future revision may swap in a small local
 * classifier while preserving this API.</p>
 *
 * <p>Signal phrases and the detection threshold are loaded once from
 * {@code /security/injection-classifier-signals.yml} on the classpath and
 * cached immutably. If the resource is missing or invalid the detector
 * fail-safes to an empty signal list and threshold {@code 1.0} so a
 * packaging/parse error cannot false-block traffic; the pattern detector
 * remains the primary gate. Operators will see an ERROR log in that case.</p>
 */
public final class ClassifierInjectionDetector {

    private static final Logger log = LoggerFactory.getLogger(ClassifierInjectionDetector.class);

    static final String RESOURCE_PATH = "/security/injection-classifier-signals.yml";

    /** Default threshold when YAML omits {@code threshold} but is otherwise valid. */
    static final double DEFAULT_THRESHOLD = 0.55;

    /**
     * Never-fire threshold used when YAML is missing or invalid.
     * Combined with an empty signal list this cannot false-block.
     */
    static final double FAIL_SAFE_THRESHOLD = 1.0;

    private static final SignalConfig CACHED = loadClasspath();

    private final List<String> signals;
    private final double threshold;

    /** Production constructor: uses the classpath-cached YAML snapshot. */
    public ClassifierInjectionDetector() {
        this(CACHED.signals(), CACHED.threshold());
    }

    /**
     * Visible for tests — inject a config snapshot without re-reading the
     * classpath (the production snapshot is immutable after class init).
     */
    ClassifierInjectionDetector(List<String> signals, double threshold) {
        this.signals = List.copyOf(signals);
        this.threshold = threshold;
    }

    List<String> signals() {
        return signals;
    }

    double threshold() {
        return threshold;
    }

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
        for (String signal : signals) {
            if (lower.contains(signal)) {
                hits++;
                if (matched == null) {
                    matched = signal;
                }
            }
        }

        double score = Math.min(1.0, hits / 3.0);
        long latency = System.nanoTime() - start;

        if (score < threshold) {
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

    private static SignalConfig loadClasspath() {
        try (InputStream in = ClassifierInjectionDetector.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                log.error("[ClassifierInjectionDetector] Missing classpath resource {}; "
                                + "fail-safe: empty signals, threshold={}",
                        RESOURCE_PATH, FAIL_SAFE_THRESHOLD);
                return SignalConfig.failSafe();
            }
            return parse(in);
        } catch (IOException ex) {
            log.error("[ClassifierInjectionDetector] Failed to read {}; fail-safe: empty signals, threshold={}",
                    RESOURCE_PATH, FAIL_SAFE_THRESHOLD, ex);
            return SignalConfig.failSafe();
        }
    }

    /**
     * Parses a YAML document of the form {@code threshold: 0.55} plus a
     * {@code signals} list. Invalid documents fail-safe rather than throw.
     */
    static SignalConfig parse(InputStream in) {
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object loaded = yaml.load(in);
            if (!(loaded instanceof Map<?, ?> map)) {
                log.error("[ClassifierInjectionDetector] YAML root must be a mapping; "
                        + "fail-safe: empty signals, threshold={}", FAIL_SAFE_THRESHOLD);
                return SignalConfig.failSafe();
            }

            Object rawThreshold = map.get("threshold");
            double threshold;
            if (rawThreshold == null) {
                threshold = DEFAULT_THRESHOLD;
            } else if (rawThreshold instanceof Number number) {
                threshold = number.doubleValue();
                if (!(threshold >= 0.0 && threshold <= 1.0)) {
                    log.error("[ClassifierInjectionDetector] Invalid threshold {}; "
                                    + "fail-safe: empty signals, threshold={}",
                            rawThreshold, FAIL_SAFE_THRESHOLD);
                    return SignalConfig.failSafe();
                }
            } else {
                log.error("[ClassifierInjectionDetector] threshold must be numeric; "
                        + "fail-safe: empty signals, threshold={}", FAIL_SAFE_THRESHOLD);
                return SignalConfig.failSafe();
            }

            Object rawSignals = map.get("signals");
            if (!(rawSignals instanceof List<?> list)) {
                log.error("[ClassifierInjectionDetector] 'signals' must be a list; "
                        + "fail-safe: empty signals, threshold={}", FAIL_SAFE_THRESHOLD);
                return SignalConfig.failSafe();
            }

            List<String> signals = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String phrase && !phrase.isBlank()) {
                    signals.add(phrase.strip().toLowerCase(Locale.ROOT));
                }
            }
            log.info("[ClassifierInjectionDetector] Loaded {} signals, threshold={} from {}",
                    signals.size(), threshold, RESOURCE_PATH);
            return new SignalConfig(List.copyOf(signals), threshold);
        } catch (RuntimeException ex) {
            log.error("[ClassifierInjectionDetector] Invalid YAML {}; fail-safe: empty signals, threshold={}",
                    RESOURCE_PATH, FAIL_SAFE_THRESHOLD, ex);
            return SignalConfig.failSafe();
        }
    }

    record SignalConfig(List<String> signals, double threshold) {
        static SignalConfig failSafe() {
            return new SignalConfig(List.of(), FAIL_SAFE_THRESHOLD);
        }
    }
}
