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
 * Utility tracking and Hebbian reinforcement relay for procedural skills (ADR-0086 §5.8, Phase 6).
 *
 * <p>Only executes when {@link SkillSignal#mode()} is {@link SkillSignal.Mode#REINFORCE}.
 * Without an outcome signal (reward != 0 or duplicateOf != null), this relay is an intentional no-op.
 * When an outcome signal exists, updates storage strength in {@link StrengthMemory}
 * to dampen power-law decay and boost procedural recall without mutating the immutable 64B encoding header.</p>
 */
public final class SkillUtilityRelay implements SynapticRelay<SkillSignal> {

    private static final Logger log = LoggerFactory.getLogger(SkillUtilityRelay.class);

    private final float utilityAlpha;
    private final StrengthMemory directStrengthMemory;
    private final Map<String, Float> utilityScores = new ConcurrentHashMap<>();

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

        // Outcome gate: ADR-0086 §5.8 "Without an outcome signal, REINFORCE is a no-op."
        boolean hasOutcome = signal.reward() != 0.0f || signal.duplicateOf() != null;
        if (!hasOutcome) {
            log.debug("SkillUtilityRelay: no outcome signal or duplicate for '{}', skipping reinforcement", targetId);
            return true;
        }

        SkillProperties config = signal.context() != null ? signal.context().find(SkillProperties.class).orElse(null) : null;
        float alpha = config != null ? config.getUtilityAlpha() : this.utilityAlpha;

        float effectiveReward = signal.reward() != 0.0f ? signal.reward() : 1.0f;
        float delta = alpha * effectiveReward;

        // Track in local utility score accumulator
        utilityScores.compute(targetId, (k, current) -> (current == null ? 0.0f : current) + delta);

        // Resolve StrengthMemory from direct field or context
        StrengthMemory strength = directStrengthMemory;
        if (strength == null && signal.context() != null) {
            strength = signal.context().find(StrengthMemory.class).orElse(null);
        }

        MemoryIndex index = signal.context() != null ? signal.context().find(MemoryIndex.class).orElse(null) : null;
        if (strength != null && index != null) {
            MemoryLocation loc = index.locate(targetId);
            if (loc != null) {
                int slot = loc.graphSlot() >= 0 ? loc.graphSlot() : (int) (loc.offset() / 164);
                strength.addAgentRecallCount(loc.type(), slot, 1);
                float currentS = strength.readStorageStrength(loc.type(), slot);
                float newS = Math.max(0.0f, currentS + delta);
                strength.writeStorageStrength(loc.type(), slot, newS);
                log.debug("SkillUtilityRelay: updated storage strength for skill '{}' slot {} to {}", targetId, slot, newS);
            }
        }

        // Notify ReinforcementHandler if available in context
        if (signal.context() != null) {
            ReinforcementHandler handler = signal.context().find(ReinforcementHandler.class).orElse(null);
            PartitionRegistry registry = signal.context().find(PartitionRegistry.class).orElse(null);
            if (handler != null && registry != null && index != null) {
                byte valence = (byte) Math.max(-128, Math.min(127, Math.round(effectiveReward * 127.0f)));
                if (valence == 0 && signal.duplicateOf() != null) {
                    valence = (byte) 64;
                }
                handler.reinforce(targetId, valence, registry, index);
            }
        }

        log.debug("SkillUtilityRelay: reinforced skill '{}' with reward {} (delta={})", targetId, effectiveReward, delta);
        return true;
    }

    public float utilityFor(final String skillId) {
        return utilityScores.getOrDefault(skillId, 0.0f);
    }

    @Override
    public String relayName() {
        return RelayNames.SKILL_UTILITY;
    }
}
