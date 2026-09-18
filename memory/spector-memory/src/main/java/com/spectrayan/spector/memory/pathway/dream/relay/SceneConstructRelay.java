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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.commons.pathway.InterruptibleRelay;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stage 6 relay in {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}.
 *
 * <h3>Biological Analog: Compositional Episodic Scene Construction</h3>
 * <p>Recombines fragmented memory primitives into synthetic scenario representations,
 * synthesizing narrative scaffolding and blending high-dimensional latent vectors under
 * temperature-modulated regularizing noise.</p>
 *
 * @since 1.4.0
 */
public final class SceneConstructRelay
        implements SynapticRelay<DreamSignal>, InterruptibleRelay {

    private static final Logger log = LoggerFactory.getLogger(SceneConstructRelay.class);

    public static final float REFERENCE_TEMPERATURE = 2.0f;
    public static final float INITIAL_SCENE_QUALITY_BASELINE = 0.50f;
    public static final int MIN_FRAGMENTS_PER_SCENE = 3;
    public static final long RNG_SEED_SALT = 42L;

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.fragments().isEmpty()) {
            return true;
        }

        List<SceneFragment> fragments = signal.fragments();
        int maxScenes = signal.config().maxDreamsPerCycle();
        float boundaryMultiplier = RemReplayRelay.computeHartmannBoundary(signal.primarySoul(), signal.config());
        float temp = signal.temperature() * boundaryMultiplier;
        float baseNoise = signal.config().dreamNoiseScale();
        float scaledNoise = baseNoise * (temp / REFERENCE_TEMPERATURE) * boundaryMultiplier;

        Random random = new Random(signal.startTime().toEpochMilli() + RNG_SEED_SALT);

        // Group fragments by role for structured slot filling
        List<SceneFragment> agents = fragments.stream().filter(f -> f.role() == FragmentRole.AGENT).toList();
        List<SceneFragment> actions = fragments.stream().filter(f -> f.role() == FragmentRole.ACTION).toList();
        List<SceneFragment> objects = fragments.stream().filter(f -> f.role() == FragmentRole.OBJECT).toList();
        List<SceneFragment> locations = fragments.stream().filter(f -> f.role() == FragmentRole.LOCATION).toList();

        int count = Math.min(maxScenes, Math.max(1, fragments.size() / MIN_FRAGMENTS_PER_SCENE));

        for (int i = 0; i < count; i++) {
            SceneFragment agent = pickFragment(agents, fragments, random, i);
            SceneFragment action = pickFragment(actions, fragments, random, i + 1);
            SceneFragment obj = pickFragment(objects, fragments, random, i + 2);
            SceneFragment loc = pickFragment(locations, fragments, random, i + 3);

            List<String> sourceIds = new ArrayList<>();
            if (agent != null) sourceIds.add(agent.sourceMemoryId());
            if (action != null && !sourceIds.contains(action.sourceMemoryId())) sourceIds.add(action.sourceMemoryId());
            if (obj != null && !sourceIds.contains(obj.sourceMemoryId())) sourceIds.add(obj.sourceMemoryId());
            if (loc != null && !sourceIds.contains(loc.sourceMemoryId())) sourceIds.add(loc.sourceMemoryId());

            // Build structured narrative text
            String agentLabel = agent != null ? agent.entityLabel() : "Agent";
            String actionLabel = action != null ? action.entityLabel() : "interacts with";
            String objectLabel = obj != null ? obj.entityLabel() : "Object";
            String locLabel = loc != null ? loc.entityLabel() : "Environment";

            String narrative = String.format("[%s Dream] %s -> %s -> %s (Context: %s)",
                    signal.mode(), agentLabel, actionLabel, objectLabel, locLabel);

            String insightDraft = String.format("Cross-domain relation: %s linked with %s through %s",
                    agentLabel, objectLabel, actionLabel);

            // If LlmProvider is wired, synthesize a creative narrative scene and cross-domain insight
            if (signal.llmProvider() != null) {
                try {
                    String soulName = signal.primarySoul() != null ? signal.primarySoul().name() : "Cognitive Companion";
                    String prompt = String.format("""
                            You are the cognitive dream engine for an autonomous agent.
                            Mode: %s
                            Active Persona: %s
                            Concept Fragments:
                            - Agent/Actor: %s
                            - Action/Transition: %s
                            - Target/Object: %s
                            - Environment/Context: %s

                            Synthesize a creative dream scenario connecting these disparate conceptual fragments.
                            Provide:
                            1. A vivid 1-2 sentence dream narrative scene describing this interaction.
                            2. A single concise sentence articulating the novel cross-domain insight or emergent hypothesis.

                            Format your response exactly as:
                            NARRATIVE: <vivid narrative scene>
                            INSIGHT: <concise cross-domain insight>
                            """,
                            signal.mode(), soulName, agentLabel, actionLabel, objectLabel, locLabel);

                    float genTemp = Math.min(1.0f, Math.max(0.1f, temp * 0.35f));
                    GenerationOptions genOptions = GenerationOptions.builder()
                            .temperature(genTemp)
                            .maxTokens(160)
                            .topP(0.9f)
                            .build();

                    String response = signal.llmProvider().generate(prompt, genOptions);
                    if (response != null && !response.isBlank()) {
                        String upper = response.toUpperCase();
                        int nIdx = upper.indexOf("NARRATIVE:");
                        int iIdx = upper.indexOf("INSIGHT:");
                        if (nIdx >= 0 && iIdx > nIdx) {
                            String parsedNarrative = response.substring(nIdx + "NARRATIVE:".length(), iIdx).trim();
                            String parsedInsight = response.substring(iIdx + "INSIGHT:".length()).trim();
                            if (!parsedNarrative.isBlank()) {
                                narrative = String.format("[%s Dream] %s", signal.mode(), parsedNarrative);
                            }
                            if (!parsedInsight.isBlank()) {
                                insightDraft = parsedInsight;
                            }
                        } else if (nIdx >= 0) {
                            String parsedNarrative = response.substring(nIdx + "NARRATIVE:".length()).trim();
                            if (!parsedNarrative.isBlank()) {
                                narrative = String.format("[%s Dream] %s", signal.mode(), parsedNarrative);
                            }
                        } else if (iIdx >= 0) {
                            String parsedInsight = response.substring(iIdx + "INSIGHT:".length()).trim();
                            if (!parsedInsight.isBlank()) {
                                insightDraft = parsedInsight;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("SceneConstructRelay: LLM synthesis failed, degrading gracefully to structural template: {}", e.getMessage());
                }
            }

            // Blend fragment vectors with temperature-scaled Gaussian noise using VectorOps SIMD
            float[] blended = blendVectors(List.of(agent, action, obj, loc), scaledNoise, random);
            if (signal.embeddingProvider() != null && signal.llmProvider() != null) {
                try {
                    float[] textVec = signal.embeddingProvider().embed(narrative).vector();
                    if (textVec != null && textVec.length > 0) {
                        float[] noise = new float[textVec.length];
                        for (int d = 0; d < textVec.length; d++) {
                            noise[d] = (float) (random.nextGaussian() * scaledNoise);
                        }
                        blended = VectorOps.add(textVec, noise);
                    }
                } catch (Exception e) {
                    log.debug("SceneConstructRelay: failed to embed synthesized narrative, using blended vector: {}", e.getMessage());
                }
            }

            DreamSignal.DreamScene scene = new DreamSignal.DreamScene(
                    signal.nextId(),
                    narrative,
                    insightDraft,
                    blended,
                    sourceIds,
                    INITIAL_SCENE_QUALITY_BASELINE,
                    null
            );

            signal.addConstructedScene(scene);
        }

        if (log.isDebugEnabled()) {
            log.debug("SceneConstructRelay: synthesized {} compositional dream scenes (temperature={}, noise={})",
                    signal.constructedScenes().size(), temp, scaledNoise);
        }

        return true;
    }

    private static SceneFragment pickFragment(List<SceneFragment> roleList, List<SceneFragment> all, Random rng, int idx) {
        if (roleList != null && !roleList.isEmpty()) {
            return roleList.get(idx % roleList.size());
        }
        return !all.isEmpty() ? all.get(rng.nextInt(all.size())) : null;
    }

    private static float[] blendVectors(List<SceneFragment> frags, float noiseScale, Random rng) {
        int dim = 0;
        for (SceneFragment f : frags) {
            if (f != null && f.embedding() != null && f.embedding().length > dim) {
                dim = f.embedding().length;
            }
        }
        if (dim == 0) return new float[0];

        float[] sum = new float[dim];
        int validCount = 0;

        for (SceneFragment f : frags) {
            if (f == null || f.embedding() == null || f.embedding().length == 0) continue;
            sum = VectorOps.add(sum, f.embedding());
            validCount++;
        }

        if (validCount > 0) {
            float[] scaled = VectorOps.scale(sum, 1.0f / validCount);
            float[] noise = new float[dim];
            for (int d = 0; d < dim; d++) {
                noise[d] = (float) (rng.nextGaussian() * noiseScale);
            }
            return VectorOps.add(scaled, noise);
        }

        return sum;
    }

    @Override
    public String relayName() {
        return "scene_construct";
    }
}
