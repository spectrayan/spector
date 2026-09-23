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
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.kernel.layout.ProvenanceLayout;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.ProvenanceEdge;
import com.spectrayan.spector.memory.pathway.RelayNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attaches multi-tier provenance audit rows and HyperEntity graph lineage edges for newly crystallized skills
 * (ADR-0086 §5.2, §5.5).
 */
public final class SkillLineageRelay implements SynapticRelay<SkillSignal> {

    private static final Logger log = LoggerFactory.getLogger(SkillLineageRelay.class);

    @Override
    public boolean transmit(final SkillSignal signal) {
        if (!signal.commit() || signal.persistedSkillId() == null || signal.parents().isEmpty()) {
            return true;
        }

        long targetTsid;
        try {
            String id = signal.persistedSkillId();
            if (id.startsWith("skill-")) {
                id = id.substring(6);
            }
            targetTsid = TsidGenerator.decodeCrockford(id);
        } catch (Exception e) {
            log.warn("Failed to decode skill TSID '{}': {}", signal.persistedSkillId(), e.getMessage());
            return true;
        }

        long now = System.currentTimeMillis();

        for (var parent : signal.parents()) {
            if (signal.provenanceMemory() != null) {
                long parentTsid;
                try {
                    String pid = parent.tsid();
                    int dashIdx = pid.lastIndexOf('-');
                    String basePart = (dashIdx != -1) ? pid.substring(0, dashIdx) : pid;
                    try {
                        parentTsid = TsidGenerator.decodeCrockford(basePart);
                    } catch (Exception notCrockford) {
                        try {
                            parentTsid = Long.parseLong(basePart);
                        } catch (NumberFormatException notLong) {
                            parentTsid = (dashIdx != -1) ? TsidGenerator.decodeCrockford(pid.substring(dashIdx + 1)) : 0L;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping unparseable parent TSID '{}'", parent.tsid());
                    parentTsid = 0L;
                }

                if (parentTsid != 0L) {
                    byte sourceKind = ProvenanceLayout.SOURCE_EPISODIC_LOG;
                    if (parent.type() == MemoryType.SEMANTIC) {
                        sourceKind = ProvenanceLayout.TARGET_SEMANTIC;
                    } else if (parent.type() == MemoryType.PROCEDURAL) {
                        sourceKind = ProvenanceLayout.TARGET_PROCEDURAL;
                    }

                    ProvenanceEdge edge = new ProvenanceEdge(
                            parentTsid,
                            targetTsid,
                            (short) 1,
                            (byte) 0,
                            (byte) 1,
                            0,
                            0,
                            0,
                            0,
                            0,
                            (short) 1,
                            (short) 0,
                            now,
                            sourceKind,
                            ProvenanceLayout.TARGET_PROCEDURAL,
                            (byte) 0
                    );

                    try {
                        signal.provenanceMemory().append(edge);
                    } catch (Exception e) {
                        log.warn("Failed to append provenance edge for skill #{}: {}", signal.persistedSkillId(), e.getMessage());
                    }
                }
            }

            if (signal.hyperEntityGraph() != null && signal.entityDirectory() != null) {
                try {
                    int parentEntityId = signal.entityDirectory().intern("memory:" + parent.tsid(), parent.type().name());
                    int skillEntityId = signal.entityDirectory().intern("skill:" + signal.persistedSkillId(), "PROCEDURAL_SKILL");

                    int[] entities = new int[]{parentEntityId, skillEntityId};
                    int[] roles = new int[]{HyperEntityGraphMemory.ROLE_SUBJECT, HyperEntityGraphMemory.ROLE_DERIVED_FROM};

                    signal.hyperEntityGraph().addHyperedge(
                            entities, roles, HyperEntityGraphMemory.TYPE_RELATIONSHIP, 1.0f, 0, now
                    );
                } catch (Exception e) {
                    log.debug("HyperEntity lineage linking skipped: {}", e.getMessage());
                }
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.SKILL_LINEAGE;
    }
}
