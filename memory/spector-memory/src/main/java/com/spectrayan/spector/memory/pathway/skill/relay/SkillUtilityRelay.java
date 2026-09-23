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
package com.spectrayan.spector.memory.pathway.skill.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.properties.SkillProperties;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.store.StrengthMemory;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.reflect.ReinforcementHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Updates procedural skill utility $U$ and Hebbian storage strength upon receiving outcome feedback
 * (ADR-0086 §5.2, §5.8).
 *
 * <p>Invariant: Without an explicit outcome reward signal ({@code reward != 0.0f}), utility reinforcement
 * is a no-op. Deduplication alone links lineage, but does not reward or inflate utility.</p>
 */
public final class SkillUtilityRelay implements SynapticRelay<SkillSignal> {

    private static final Logger log = LoggerFactory.getLogger(SkillUtilityRelay.class);

    private final float utilityAlpha;
    private final StrengthMemory directStrengthMemory;
    private final Map<String, Float> inMemoryUtilityDeltas = new ConcurrentHashMap<>();

    public SkillUtilityRelay() {
        this(0.1f, null);
    }

    public SkillUtilityRelay(final float utilityAlpha) {
        this(utilityAlpha, null);
    }

    public SkillUtilityRelay(final float utilityAlpha, final StrengthMemory strengthMemory) {
        this.utilityAlpha = utilityAlpha;
        this.directStrengthMemory = strengthMemory;
    }

    @Override
    public boolean transmit(final SkillSignal signal) {
        if (signal.mode() != SkillSignal.Mode.REINFORCE) {
            return true;
        }

        // Outcome gate: ADR-0086 §5.8 "Without an outcome signal, REINFORCE is a no-op."
        // Dedup is near-duplicate detection, not a success reward.
        if (signal.reward() == 0.0f) {
            log.debug("SkillUtilityRelay: no outcome reward for skill signal, skipping utility learning");
            return true;
        }

        String targetId = signal.skillId();
        if (targetId == null) {
            targetId = signal.duplicateOf();
        }
        if (targetId == null) {
            targetId = signal.persistedSkillId();
        }
        if (targetId == null) {
            return true;
        }

        SkillProperties config = signal.context() != null ? signal.context().find(SkillProperties.class).orElse(null) : null;
        float alpha = config != null ? config.getUtilityAlpha() : this.utilityAlpha;

        float reward = signal.reward();
        float delta = alpha * reward;

        inMemoryUtilityDeltas.compute(targetId, (k, current) -> (current == null ? 0.0f : current) + delta);

        // Resolve StrengthMemory from direct field or context (the authoritative ground truth)
        StrengthMemory strength = directStrengthMemory;
        if (strength == null && signal.context() != null) {
            strength = signal.context().find(StrengthMemory.class).orElse(null);
        }

        MemoryIndex index = signal.context() != null ? signal.context().find(MemoryIndex.class).orElse(null) : null;
        if (strength != null && index != null) {
            MemoryLocation loc = index.locate(targetId);
            if (loc != null && loc.graphSlot() >= 0) {
                int slot = loc.graphSlot();
                strength.addAgentRecallCount(loc.type(), slot, 1);
                float currentS = strength.readStorageStrength(loc.type(), slot);
                float newS = Math.max(0.0f, currentS + delta);
                strength.writeStorageStrength(loc.type(), slot, newS);
                log.debug("SkillUtilityRelay: updated storage strength for skill '{}' slot {} to {}", targetId, slot, newS);
            } else {
                log.debug("SkillUtilityRelay: skipping strength write for skill '{}': no valid slot index", targetId);
            }
        }

        // Notify ReinforcementHandler if available in context
        if (signal.context() != null) {
            ReinforcementHandler handler = signal.context().find(ReinforcementHandler.class).orElse(null);
            PartitionRegistry registry = signal.context().find(PartitionRegistry.class).orElse(null);
            if (handler != null && registry != null && index != null) {
                byte valence = (byte) Math.max(-128, Math.min(127, Math.round(reward * 127.0f)));
                handler.reinforce(targetId, valence, registry, index);
            }
        }

        return true;
    }

    /**
     * Returns accumulated in-memory delta for a given skill (primarily for test assertions).
     */
    public float getUtilityScore(final String skillId) {
        return inMemoryUtilityDeltas.getOrDefault(skillId, 0.0f);
    }

    /**
     * Alias for {@link #getUtilityScore(String)}.
     */
    public float utilityFor(final String skillId) {
        return getUtilityScore(skillId);
    }

    @Override
    public String relayName() {
        return RelayNames.SKILL_UTILITY;
    }
}
