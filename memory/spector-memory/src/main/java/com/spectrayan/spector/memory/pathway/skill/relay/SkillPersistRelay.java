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

import com.spectrayan.spector.commons.pathway.PathwayCatalog;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.kernel.api.EngramSource;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists compiled procedural skills via nested {@link RememberPathway} with {@code FLAG_CRYSTALLIZED}
 * and dynamic importance scoring (ADR-0086 §5.2, §5.9).
 */
public final class SkillPersistRelay implements SynapticRelay<SkillSignal> {

    private static final Logger log = LoggerFactory.getLogger(SkillPersistRelay.class);
    private static final TsidGenerator TSID = new TsidGenerator();

    @Override
    public boolean transmit(final SkillSignal signal) {
        if (signal.mode() == SkillSignal.Mode.REINFORCE || !signal.commit() || signal.extractedBody() == null) {
            return true;
        }

        PathwayCatalog catalog = signal.context() != null ? signal.context().catalog() : null;
        if (catalog == null) {
            log.warn("SkillPersistRelay: missing PathwayCatalog in context");
            return false;
        }

        var rememberPathwayOpt = catalog.find(RememberPathway.class);
        if (rememberPathwayOpt.isEmpty()) {
            log.warn("SkillPersistRelay: RememberPathway not found in catalog");
            return false;
        }

        String skillId = signal.persistedSkillId();
        if (skillId == null) {
            skillId = TSID.generate();
            signal.persistedSkillId(skillId);
        }

        short soulVer = signal.context() != null
                ? signal.context().find(SoulVersionSource.class)
                        .map(SoulVersionSource::currentSoulVersion).orElse((short) 0)
                : (short) 0;

        String serializedText = signal.extractedBody().serialize();

        // Mint prior: low initial importance (analog of ACT-R U0 ≈ 0) to avoid dominating fused recall.
        // Pre-construct EncodingHeader with 0.15f importance and NF7 SOURCE_DISTILLED (EngramSource.DISTILLED)
        // so DopaminergicSurpriseRelay preserves the mint prior rather than overwriting it with ICNU fusion.
        EncodingHeader header = new EncodingHeader(
                System.currentTimeMillis(),
                0L,
                0L,
                0.0f,
                0.15f,
                0,
                (short) 0,
                (byte) 0,
                EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.PROCEDURAL.ordinal()),
                (byte) 0,
                1.0f,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                soulVer,
                0.0f,
                EncodingHeaderFields.FLAG_CRYSTALLIZED,
                EngramSource.DISTILLED
        );

        RememberSignal rs = RememberSignal.forCognitiveWithHeader(
                skillId,
                serializedText,
                null,
                MemoryType.PROCEDURAL,
                new String[]{"procedural", "skill", "crystallized"},
                MemorySource.PROCEDURAL,
                header
        );
        rs.consolidationFlagsOverlay(EncodingHeaderFields.FLAG_CRYSTALLIZED);

        try {
            catalog.invoke(RememberPathway.class, signal.context(), rs);
            log.debug("SkillPersistRelay: successfully persisted skill #{}", skillId);
        } catch (Exception e) {
            log.error("SkillPersistRelay: failed to persist skill #{}: {}", skillId, e.getMessage(), e);
            return false;
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.SKILL_PERSIST;
    }
}
