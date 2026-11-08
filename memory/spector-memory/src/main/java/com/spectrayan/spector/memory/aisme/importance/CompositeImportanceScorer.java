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
package com.spectrayan.spector.memory.aisme.importance;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.cognitive.CompositeImportanceKernel;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.UserSoul;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Evaluator synthesizing multi-dimensional neurocognitive signals into composite importance scores \(I(o_t)\).
 *
 * <h3>Biological Analog: Multi-Limbic Salience Synthesis</h3>
 * <p>Integrates epistemic prediction error (Hippocampus/PFC), affective valence & arousal (Amygdala/Insula),
 * prospective goal alignment (dlPFC), social context (ACC), and manifold novelty.</p>
 */
public final class CompositeImportanceScorer {

    private static final Logger log = LoggerFactory.getLogger(CompositeImportanceScorer.class);

    private final AismeProperties config;
    private final float[] baseWeights;
    private final List<float[]> activeGoalEmbeddings = new CopyOnWriteArrayList<>();

    public CompositeImportanceScorer(AismeProperties config) {
        if (config == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "config must not be null");
        }
        this.config = config;
        this.baseWeights = CompositeImportanceKernel.normalizeWeights(new float[]{
                config.importanceWeightSurprise(),
                config.importanceWeightAffect(),
                config.importanceWeightGoal(),
                config.importanceWeightSocial(),
                config.importanceWeightNovelty()
        });
        log.info("Initialized CompositeImportanceScorer with base weights: [S={:.2f}, A={:.2f}, G={:.2f}, Soc={:.2f}, N={:.2f}], flashbulbThreshold={}",
                baseWeights[0], baseWeights[1], baseWeights[2], baseWeights[3], baseWeights[4], config.importanceFlashbulbThreshold());
    }

    /**
     * Registers active goal / intention embeddings for prospective relevance scoring.
     *
     * @param goalEmbeddings list of L2-normalized goal vectors
     */
    public void setGoalEmbeddings(List<float[]> goalEmbeddings) {
        activeGoalEmbeddings.clear();
        if (goalEmbeddings != null) {
            for (float[] g : goalEmbeddings) {
                if (g != null && g.length > 0) {
                    activeGoalEmbeddings.add(g);
                }
            }
        }
    }

    /**
     * Evaluates composite importance for a structured signal vector and active cognitive profile.
     *
     * @param signals component neurocognitive signals
     * @param profile active cognitive profile (nullable, defaults to configured weights)
     * @return normalized scalar importance \(I(o_t) \in [0.0, 1.0]\)
     */
    public float evaluate(CompositeImportanceSignals signals, CognitiveProfile profile) {
        if (signals == null) {
            return 0.0f;
        }
        if (!config.enableImportance()) {
            return 0.5f;
        }

        float[] weights = weightsForProfile(profile);
        return CompositeImportanceKernel.evaluate(signals.toArray(), weights);
    }

    /**
     * Extracts component signals from an in-flight {@link RememberSignal} and computes composite importance.
     *
     * @param signal in-flight remember signal
     * @param profile active cognitive profile
     * @return normalized scalar importance \(I(o_t) \in [0.0, 1.0]\)
     */
    public float evaluateSignal(RememberSignal signal, CognitiveProfile profile) {
        return evaluateSignal(signal, profile, null);
    }

    /**
     * Extracts component signals from an in-flight {@link RememberSignal} using the given locale
     * and computes composite importance.
     *
     * @param signal in-flight remember signal
     * @param profile active cognitive profile
     * @param locale optional locale for keyword fallback selection
     * @return normalized scalar importance \(I(o_t) \in [0.0, 1.0]\)
     */
    public float evaluateSignal(RememberSignal signal, CognitiveProfile profile, Locale locale) {
        if (signal == null) {
            return 0.0f;
        }
        if (!config.enableImportance()) {
            return signal.importance() > 0.0f ? signal.importance() : 0.5f;
        }

        CompositeImportanceSignals signals = extractSignals(signal, locale);
        return evaluate(signals, profile);
    }

    /**
     * Extracts component signals from a remember signal payload.
     *
     * @param signal remember signal
     * @return extracted 5-dimensional signals
     */
    public CompositeImportanceSignals extractSignals(RememberSignal signal) {
        return extractSignals(signal, null);
    }

    /**
     * Extracts component signals from a remember signal payload using optional locale-specific
     * keyword fallbacks when structured pipeline signals are not present.
     *
     * @param signal remember signal
     * @param locale optional locale for keyword fallback lookup
     * @return extracted 5-dimensional signals
     */
    public CompositeImportanceSignals extractSignals(RememberSignal signal, Locale locale) {
        if (signal == null) {
            return CompositeImportanceSignals.neutral();
        }

        Locale resolvedLocale = locale != null ? locale : extractLocaleFromTags(signal.tags());

        // 1. Surprise s1
        float surprise = 0.1f;
        EventDensityMetrics edm = signal.eventDensityMetrics();
        if (edm != null) {
            surprise = edm.surprisal();
        } else if (signal.nearestDist() > 0.0f) {
            surprise = Math.min(1.0f, signal.nearestDist());
        }

        // 2. Affect s2 — prefer pipeline-computed salience and appraised header valence/arousal
        float affect = 0.0f;
        if (signal.salienceProfile() != null && !signal.salienceProfile().isNeutral()) {
            affect = 0.6f;
        }
        EncodingHeader header = signal.header();
        if (header != null) {
            float valenceMag = Math.min(1.0f, Math.abs((int) header.valence()) / 127.0f);
            float arousalMag = Math.min(1.0f, Byte.toUnsignedInt(header.arousal()) / 255.0f);
            affect = Math.max(affect, Math.max(valenceMag, arousalMag));
        }

        // 3. Goal Relevance s3 — prefer active goal embeddings and soul purpose embeddings
        float goalRelevance = 0.0f;
        float[] vec = signal.vector();
        if (vec != null && !activeGoalEmbeddings.isEmpty()) {
            for (float[] goalVec : activeGoalEmbeddings) {
                if (goalVec.length == vec.length) {
                    float sim = CosineSimilarity.compute(vec, goalVec);
                    if (sim > goalRelevance) {
                        goalRelevance = sim;
                    }
                }
            }
            goalRelevance = Math.max(0.0f, Math.min(1.0f, goalRelevance));
        }
        if (vec != null && signal.soulContexts() != null) {
            for (SoulContext sc : signal.soulContexts()) {
                if (sc != null && sc.identityEmbedding() != null && sc.identityEmbedding().length == vec.length) {
                    float sim = CosineSimilarity.compute(vec, sc.identityEmbedding());
                    if (sim > goalRelevance) {
                        goalRelevance = Math.min(1.0f, sim);
                    }
                }
            }
        }

        // 4. Social Context s4 & structured tag signals for affect/goal/social
        float socialContext = 0.0f;
        if (signal.soulContexts() != null) {
            for (SoulContext sc : signal.soulContexts()) {
                if (sc instanceof UserSoul) {
                    socialContext = Math.max(socialContext, 0.80f);
                }
            }
        }
        if (signal.tags() != null) {
            for (String rawTag : signal.tags()) {
                if (rawTag == null || rawTag.isEmpty()) {
                    continue;
                }
                String tag = rawTag.toLowerCase(Locale.ROOT);
                if (tag.startsWith("user:") || tag.startsWith("author:") || tag.startsWith("speaker:")
                        || tag.startsWith("interlocutor:") || tag.startsWith("person:")
                        || tag.startsWith("entity:person:") || tag.startsWith("relation:")
                        || tag.contains("@")) {
                    socialContext = Math.max(socialContext, 0.80f);
                }
                if (tag.startsWith("intent:agreement") || tag.startsWith("intent:promise")
                        || tag.startsWith("intent:commitment") || tag.startsWith("relation:agreement")
                        || tag.startsWith("relation:promise")) {
                    socialContext = Math.max(socialContext, 0.90f);
                }
                if (tag.startsWith("affect:high") || tag.startsWith("emotion:high")
                        || tag.startsWith("severity:critical") || tag.startsWith("severity:emergency")) {
                    affect = Math.max(affect, 0.95f);
                } else if (tag.startsWith("affect:") || tag.startsWith("emotion:")
                        || tag.startsWith("sentiment:") || tag.startsWith("severity:error")
                        || tag.startsWith("severity:warn")) {
                    affect = Math.max(affect, 0.80f);
                }
                if (tag.startsWith("intent:critical") || tag.startsWith("intent:emergency")
                        || tag.startsWith("goal:critical") || tag.startsWith("priority:critical")) {
                    goalRelevance = Math.max(goalRelevance, 0.90f);
                } else if (tag.startsWith("intent:goal") || tag.startsWith("intent:task")
                        || tag.startsWith("intent:commit") || tag.startsWith("intent:plan")
                        || tag.startsWith("intent:milestone") || tag.startsWith("goal:")
                        || tag.startsWith("task:") || tag.startsWith("milestone:")
                        || tag.startsWith("todo:")) {
                    goalRelevance = Math.max(goalRelevance, 0.85f);
                }
            }
        }

        // Token-boundary keyword fallback (Locale.ROOT normalized, configurable & locale-aware)
        String textLower = signal.text() != null ? signal.text().toLowerCase(Locale.ROOT) : "";
        if (!textLower.isEmpty()) {
            if (containsAnyTokenBoundedKeyword(textLower,
                    effectiveKeywords(config.importanceAffectHighKeywords(), config.importanceLocaleAffectHighKeywords(), resolvedLocale))) {
                affect = Math.max(affect, 0.95f);
            } else if (containsAnyTokenBoundedKeyword(textLower,
                    effectiveKeywords(config.importanceAffectMediumKeywords(), config.importanceLocaleAffectMediumKeywords(), resolvedLocale))) {
                affect = Math.max(affect, 0.80f);
            }

            if (containsAnyTokenBoundedKeyword(textLower,
                    effectiveKeywords(config.importanceGoalHighKeywords(), config.importanceLocaleGoalHighKeywords(), resolvedLocale))) {
                goalRelevance = Math.max(goalRelevance, 0.90f);
            } else if (containsAnyTokenBoundedKeyword(textLower,
                    effectiveKeywords(config.importanceGoalMediumKeywords(), config.importanceLocaleGoalMediumKeywords(), resolvedLocale))) {
                goalRelevance = Math.max(goalRelevance, 0.85f);
            }

            if (containsAnyTokenBoundedKeyword(textLower,
                    effectiveKeywords(config.importanceSocialKeywords(), config.importanceLocaleSocialKeywords(), resolvedLocale))) {
                socialContext = Math.max(socialContext, 0.90f);
            }
        }

        // 5. Novelty s5
        float novelty = 0.1f;
        if (signal.nearestDist() > 0.0f) {
            novelty = Math.clamp(signal.nearestDist() / 2.0f, 0.0f, 1.0f);
        } else if (edm != null && edm.isSalientSpike()) {
            novelty = 0.80f;
        }

        return new CompositeImportanceSignals(surprise, affect, goalRelevance, socialContext, novelty);
    }

    private static Locale extractLocaleFromTags(String[] tags) {
        if (tags == null) {
            return null;
        }
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            String lower = raw.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("locale:") && lower.length() > "locale:".length()) {
                return Locale.forLanguageTag(lower.substring("locale:".length()).replace('_', '-'));
            }
            if (lower.startsWith("lang:") && lower.length() > "lang:".length()) {
                return Locale.forLanguageTag(lower.substring("lang:".length()).replace('_', '-'));
            }
        }
        return null;
    }

    private static List<String> effectiveKeywords(
            List<String> baseKeywords,
            Map<String, List<String>> localeOverrides,
            Locale resolvedLocale) {
        if (resolvedLocale != null && localeOverrides != null && !localeOverrides.isEmpty()) {
            String fullTag = resolvedLocale.toLanguageTag().replace('_', '-').toLowerCase(Locale.ROOT);
            List<String> exact = localeOverrides.get(fullTag);
            if (exact != null) {
                return exact;
            }
            String language = resolvedLocale.getLanguage().toLowerCase(Locale.ROOT);
            List<String> byLang = localeOverrides.get(language);
            if (byLang != null) {
                return byLang;
            }
        }
        if (localeOverrides == null || localeOverrides.isEmpty()) {
            return baseKeywords != null ? baseKeywords : List.of();
        }
        List<String> combined = new ArrayList<>(baseKeywords != null ? baseKeywords : List.of());
        for (List<String> extra : localeOverrides.values()) {
            if (extra != null) {
                combined.addAll(extra);
            }
        }
        return combined;
    }

    static boolean containsAnyTokenBoundedKeyword(String textLower, Collection<String> keywords) {
        if (textLower == null || textLower.isEmpty() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String rawKeyword : keywords) {
            if (rawKeyword == null) {
                continue;
            }
            String kw = rawKeyword.trim().toLowerCase(Locale.ROOT);
            if (!kw.isEmpty() && containsTokenBoundedPhrase(textLower, kw)) {
                return true;
            }
        }
        return false;
    }

    static boolean containsTokenBoundedPhrase(String textLower, String keywordLower) {
        int kwLen = keywordLower.length();
        int textLen = textLower.length();
        if (kwLen == 0 || kwLen > textLen) {
            return false;
        }
        int fromIndex = 0;
        while (fromIndex <= textLen - kwLen) {
            int idx = textLower.indexOf(keywordLower, fromIndex);
            if (idx < 0) {
                return false;
            }
            int end = idx + kwLen;
            boolean leftBoundary = idx == 0 || !isWordBoundaryChar(textLower.codePointBefore(idx));
            boolean rightBoundary = end == textLen || !isWordBoundaryChar(textLower.codePointAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            fromIndex = idx + 1;
        }
        return false;
    }

    private static boolean isWordBoundaryChar(int codePoint) {
        return Character.isLetterOrDigit(codePoint) && !Character.isIdeographic(codePoint);
    }

    /**
     * Resolves the normalized 5-dimensional weight vector for the given cognitive profile.
     *
     * @param profile active cognitive profile
     * @return 5-element normalized weight array
     */
    public float[] weightsForProfile(CognitiveProfile profile) {
        if (profile == null) {
            return baseWeights;
        }

        return switch (profile) {
            case DEBUGGING, PARANOID_SENTINEL ->
                // High surprise & affect
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.35f, 0.25f, 0.20f, 0.10f, 0.10f});
            case EXPLORING, DIVERGENT ->
                // High novelty & surprise
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.30f, 0.10f, 0.15f, 0.10f, 0.35f});
            case THE_EXECUTOR, SYSTEMATIZER ->
                // High goal relevance & social
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.15f, 0.05f, 0.50f, 0.20f, 0.10f});
            case HIGHLY_SENSITIVE, RECALLING ->
                // High affect & social context
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.10f, 0.40f, 0.15f, 0.30f, 0.05f});
            case CRITICAL ->
                // Heavy goal & affect
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.25f, 0.25f, 0.35f, 0.10f, 0.05f});
            default ->
                    baseWeights;
        };
    }

    /**
     * Tests whether a composite importance score crosses the flashbulb threshold.
     *
     * @param importanceScore evaluated composite score
     * @return true if score meets or exceeds flashbulb threshold
     */
    public boolean isFlashbulb(float importanceScore) {
        return importanceScore >= config.importanceFlashbulbThreshold();
    }

    public AismeProperties config() {
        return config;
    }
}
