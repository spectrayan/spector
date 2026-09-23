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

import com.spectrayan.spector.commons.pathway.AbstractSignal;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.ProvenanceMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.pathway.skill.model.SkillBody;

import java.util.List;

public final class SkillSignal extends AbstractSignal {

    public enum Mode {
        COMPILE,
        DRY_RUN,
        REINFORCE
    }

    public record ParentRef(String tsid, MemoryType type) {}

    private final Mode mode;
    private final List<ParentRef> parents;
    private final String cue;
    private final boolean commit;
    private final String skillId;
    private final float reward;

    private final HyperEntityGraphMemory hyperEntityGraph;
    private final EntityDirectory entityDirectory;
    private final ProvenanceMemory provenanceMemory;

    private SkillBody extractedBody;
    private String duplicateOf;
    private String persistedSkillId;

    public SkillSignal(
            final Mode mode,
            final List<ParentRef> parents,
            final String cue,
            final boolean commit,
            final String skillId,
            final float reward,
            final HyperEntityGraphMemory hyperEntityGraph,
            final EntityDirectory entityDirectory,
            final ProvenanceMemory provenanceMemory) {
        this.mode = mode != null ? mode : Mode.DRY_RUN;
        this.parents = parents != null ? List.copyOf(parents) : List.of();
        this.cue = cue;
        this.commit = this.mode != Mode.DRY_RUN && commit;
        this.skillId = skillId;
        this.reward = reward;
        this.hyperEntityGraph = hyperEntityGraph;
        this.entityDirectory = entityDirectory;
        this.provenanceMemory = provenanceMemory;
    }

    public Mode mode() { return mode; }
    public List<ParentRef> parents() { return parents; }
    public String cue() { return cue; }
    public boolean commit() { return commit; }
    public String skillId() { return skillId; }
    public float reward() { return reward; }

    public HyperEntityGraphMemory hyperEntityGraph() { return hyperEntityGraph; }
    public EntityDirectory entityDirectory() { return entityDirectory; }
    public ProvenanceMemory provenanceMemory() { return provenanceMemory; }

    public SkillBody extractedBody() { return extractedBody; }
    public void extractedBody(final SkillBody extractedBody) { this.extractedBody = extractedBody; }

    public String duplicateOf() { return duplicateOf; }
    public void duplicateOf(final String duplicateOf) { this.duplicateOf = duplicateOf; }

    public String persistedSkillId() { return persistedSkillId; }
    public void persistedSkillId(final String persistedSkillId) { this.persistedSkillId = persistedSkillId; }
}
