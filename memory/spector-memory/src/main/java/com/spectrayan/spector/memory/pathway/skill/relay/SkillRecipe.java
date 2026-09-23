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

    private final SkillPersistRelay persistRelay;
    private final SkillLineageRelay lineageRelay;

    private SkillRecipe(final Builder builder) {
        this.persistRelay = Objects.requireNonNull(builder.persistRelay, "persistRelay cannot be null");
        this.lineageRelay = Objects.requireNonNull(builder.lineageRelay, "lineageRelay cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void compose(final PathwayComposer<SkillSignal> composer) {
        // Nested Remember invocation behind shared breaker and bulkhead
        composer.stage(RelayNames.SKILL_PERSIST)
                .relay(persistRelay)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .breaker(PathwayResilience.nestedRemember())
                .bulkhead(PathwayResilience.PATHWAY_REMEMBER,
                        PathwayResilience.nestedRememberBulkhead())
                .add();

        // Lineage tracking (provenance + hypergraph)
        composer.stage(RelayNames.SKILL_LINEAGE)
                .relay(lineageRelay)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .add();
    }

    public static final class Builder {
        private SkillPersistRelay persistRelay;
        private SkillLineageRelay lineageRelay;

        public Builder persistRelay(final SkillPersistRelay persistRelay) {
            this.persistRelay = persistRelay;
            return this;
        }

        public Builder lineageRelay(final SkillLineageRelay lineageRelay) {
            this.lineageRelay = lineageRelay;
            return this;
        }

        public SkillRecipe build() {
            return new SkillRecipe(this);
        }
    }
}
