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

import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.memory.pathway.PathwayResilience;
import com.spectrayan.spector.memory.pathway.RelayNames;

import java.util.Objects;

/**
 * Declarative recipe wiring the relays of the {@link com.spectrayan.spector.memory.pathway.skill.SkillPathway}
 * (ADR-0086 §5.2).
 */
public final class SkillRecipe implements PathwayRecipe<SkillSignal> {

    private final ClusterAdmitRelay admitRelay;
    private final SkillExtractRelay extractRelay;
    private final SkillDedupRelay dedupRelay;
    private final SkillPersistRelay persistRelay;
    private final SkillLineageRelay lineageRelay;
    private final SkillUtilityRelay utilityRelay;

    private SkillRecipe(final Builder builder) {
        this.admitRelay = Objects.requireNonNull(builder.admitRelay, "admitRelay cannot be null");
        this.extractRelay = Objects.requireNonNull(builder.extractRelay, "extractRelay cannot be null");
        this.dedupRelay = Objects.requireNonNull(builder.dedupRelay, "dedupRelay cannot be null");
        this.persistRelay = Objects.requireNonNull(builder.persistRelay, "persistRelay cannot be null");
        this.lineageRelay = Objects.requireNonNull(builder.lineageRelay, "lineageRelay cannot be null");
        this.utilityRelay = Objects.requireNonNull(builder.utilityRelay, "utilityRelay cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void compose(final PathwayComposer<SkillSignal> composer) {
        // 1. Cluster Admission
        composer.stage(RelayNames.SKILL_ADMIT)
                .relay(admitRelay)
                .policy(ErrorPolicy.FAIL_FAST)
                .add();

        // 2. Structured Skill Extraction
        composer.stage(RelayNames.SKILL_EXTRACT)
                .relay(extractRelay)
                .policy(ErrorPolicy.FAIL_FAST)
                .add();

        // 3. Deduplication
        composer.stage(RelayNames.SKILL_DEDUP)
                .relay(dedupRelay)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .add();

        // 4. Nested Remember invocation behind shared breaker and bulkhead
        composer.stage(RelayNames.SKILL_PERSIST)
                .relay(persistRelay)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .breaker(PathwayResilience.nestedRemember())
                .bulkhead(PathwayResilience.PATHWAY_REMEMBER,
                        PathwayResilience.nestedRememberBulkhead())
                .add();

        // 5. Lineage tracking (provenance + hypergraph)
        composer.stage(RelayNames.SKILL_LINEAGE)
                .relay(lineageRelay)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .add();

        // 6. Utility tracking & Hebbian reinforcement (ADR-0086 §5.8)
        composer.stage(RelayNames.SKILL_UTILITY)
                .relay(utilityRelay)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .add();
    }

    public ClusterAdmitRelay admitRelay() { return admitRelay; }
    public SkillExtractRelay extractRelay() { return extractRelay; }
    public SkillDedupRelay dedupRelay() { return dedupRelay; }
    public SkillPersistRelay persistRelay() { return persistRelay; }
    public SkillLineageRelay lineageRelay() { return lineageRelay; }
    public SkillUtilityRelay utilityRelay() { return utilityRelay; }

    public static final class Builder {
        private ClusterAdmitRelay admitRelay = new ClusterAdmitRelay();
        private SkillExtractRelay extractRelay = new SkillExtractRelay();
        private SkillDedupRelay dedupRelay = new SkillDedupRelay();
        private SkillPersistRelay persistRelay = new SkillPersistRelay();
        private SkillLineageRelay lineageRelay = new SkillLineageRelay();
        private SkillUtilityRelay utilityRelay = new SkillUtilityRelay();

        public Builder admitRelay(final ClusterAdmitRelay admitRelay) {
            this.admitRelay = admitRelay;
            return this;
        }

        public Builder extractRelay(final SkillExtractRelay extractRelay) {
            this.extractRelay = extractRelay;
            return this;
        }

        public Builder dedupRelay(final SkillDedupRelay dedupRelay) {
            this.dedupRelay = dedupRelay;
            return this;
        }

        public Builder persistRelay(final SkillPersistRelay persistRelay) {
            this.persistRelay = persistRelay;
            return this;
        }

        public Builder lineageRelay(final SkillLineageRelay lineageRelay) {
            this.lineageRelay = lineageRelay;
            return this;
        }

        public Builder utilityRelay(final SkillUtilityRelay utilityRelay) {
            this.utilityRelay = utilityRelay;
            return this;
        }

        public SkillRecipe build() {
            return new SkillRecipe(this);
        }
    }
}
