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

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable signal driving the {@link com.spectrayan.spector.memory.pathway.skill.SkillPathway} (ADR-0086 §5.2).
 */
public final class SkillSignal extends AbstractSignal {

    public enum Mode {
        COMPILE,
        DRY_RUN,
        REINFORCE
    }

    public record ParentRef(
            String id,
            MemoryType type,
            long tsid,
            long sessionId,
            int firstSeq,
            int lastSeq,
            String text
    ) {
        public ParentRef(String id, MemoryType type) {
            this(id, type, parseTsid(id), parseSessionId(id), 0, 0, null);
        }

        public ParentRef(String id, MemoryType type, String text) {
            this(id, type, parseTsid(id), parseSessionId(id), 0, 0, text);
        }

        public static ParentRef episodic(long sessionId, int sequenceId, String text) {
            return new ParentRef(sessionId + "#" + sequenceId, MemoryType.EPISODIC, 0L, sessionId, sequenceId, sequenceId, text);
        }

        public static ParentRef episodic(long sessionId, int firstSeq, int lastSeq, String text) {
            return new ParentRef(sessionId + "#" + firstSeq + "-" + lastSeq, MemoryType.EPISODIC, 0L, sessionId, firstSeq, lastSeq, text);
        }

        public static ParentRef semantic(long tsid, String text) {
            return new ParentRef("sem-" + com.spectrayan.spector.kernel.id.TsidGenerator.encodeCrockford(tsid), MemoryType.SEMANTIC, tsid, 0L, 0, 0, text);
        }

        public static ParentRef procedural(long tsid, String text) {
            return new ParentRef("skill-" + com.spectrayan.spector.kernel.id.TsidGenerator.encodeCrockford(tsid), MemoryType.PROCEDURAL, tsid, 0L, 0, 0, text);
        }

        public static ParentRef of(String id, MemoryType type) {
            return new ParentRef(id, type);
        }

        private static long parseTsid(String id) {
            if (id == null || id.isBlank()) return 0L;
            try {
                int dashIdx = id.lastIndexOf('-');
                String token = dashIdx >= 0 ? id.substring(dashIdx + 1) : id;
                return com.spectrayan.spector.kernel.id.TsidGenerator.decodeCrockford(token);
            } catch (Exception e) {
                try {
                    return Long.parseLong(id);
                } catch (Exception ignored) {
                    return 0L;
                }
            }
        }

        private static long parseSessionId(String id) {
            if (id == null || id.isBlank()) return 0L;
            try {
                int hashIdx = id.indexOf('#');
                if (hashIdx >= 0) {
                    return Long.parseLong(id.substring(0, hashIdx));
                }
            } catch (Exception ignored) {}
            return 0L;
        }
    }

    private Mode mode;
    private final List<ParentRef> parents;
    private final List<String> parentTexts;
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
    private float[] vector;

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
        this(mode, parents, List.of(), cue, commit, skillId, reward, hyperEntityGraph, entityDirectory, provenanceMemory);
    }

    public SkillSignal(
            final Mode mode,
            final List<ParentRef> parents,
            final List<String> parentTexts,
            final String cue,
            final boolean commit,
            final String skillId,
            final float reward,
            final HyperEntityGraphMemory hyperEntityGraph,
            final EntityDirectory entityDirectory,
            final ProvenanceMemory provenanceMemory) {
        this.mode = mode != null ? mode : Mode.DRY_RUN;
        this.parents = parents != null ? List.copyOf(parents) : List.of();
        if (parentTexts != null && !parentTexts.isEmpty()) {
            this.parentTexts = List.copyOf(parentTexts);
        } else {
            this.parentTexts = this.parents.stream()
                    .map(ParentRef::text)
                    .filter(t -> t != null && !t.isBlank())
                    .toList();
        }
        this.cue = cue;
        this.commit = this.mode != Mode.DRY_RUN && commit;
        this.skillId = skillId;
        this.reward = reward;
        this.hyperEntityGraph = hyperEntityGraph;
        this.entityDirectory = entityDirectory;
        this.provenanceMemory = provenanceMemory;
    }

    public Mode mode() { return mode; }
    public void mode(final Mode mode) { this.mode = mode != null ? mode : Mode.DRY_RUN; }

    public List<ParentRef> parents() { return parents; }
    public List<String> parentTexts() { return parentTexts; }
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

    public float[] vector() { return vector; }
    public void vector(final float[] vector) { this.vector = vector; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Mode mode = Mode.COMPILE;
        private final List<ParentRef> parents = new ArrayList<>();
        private final List<String> parentTexts = new ArrayList<>();
        private String cue;
        private boolean commit = true;
        private String skillId;
        private float reward;
        private HyperEntityGraphMemory hyperEntityGraph;
        private EntityDirectory entityDirectory;
        private ProvenanceMemory provenanceMemory;
        private SkillBody extractedBody;

        public Builder mode(final Mode mode) { this.mode = mode; return this; }
        public Builder parent(final String tsid, final MemoryType type) { this.parents.add(new ParentRef(tsid, type)); return this; }
        public Builder parent(final ParentRef parent) { if (parent != null) this.parents.add(parent); return this; }
        public Builder parents(final List<ParentRef> parents) { if (parents != null) this.parents.addAll(parents); return this; }
        public Builder parentText(final String text) { if (text != null && !text.isBlank()) this.parentTexts.add(text); return this; }
        public Builder parentTexts(final List<String> texts) { if (texts != null) this.parentTexts.addAll(texts); return this; }
        public Builder cue(final String cue) { this.cue = cue; return this; }
        public Builder commit(final boolean commit) { this.commit = commit; return this; }
        public Builder skillId(final String skillId) { this.skillId = skillId; return this; }
        public Builder reward(final float reward) { this.reward = reward; return this; }
        public Builder hyperEntityGraph(final HyperEntityGraphMemory h) { this.hyperEntityGraph = h; return this; }
        public Builder entityDirectory(final EntityDirectory e) { this.entityDirectory = e; return this; }
        public Builder provenanceMemory(final ProvenanceMemory p) { this.provenanceMemory = p; return this; }
        public Builder extractedBody(final SkillBody b) { this.extractedBody = b; return this; }

        public SkillSignal build() {
            var signal = new SkillSignal(mode, parents, parentTexts, cue, commit, skillId, reward, hyperEntityGraph, entityDirectory, provenanceMemory);
            signal.extractedBody(extractedBody);
            return signal;
        }
    }
}
