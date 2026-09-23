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
package com.spectrayan.spector.memory.pathway.skill;

import com.spectrayan.spector.commons.pathway.AbstractPathway;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillLineageRelay;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillPersistRelay;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillRecipe;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillReport;
import com.spectrayan.spector.memory.pathway.skill.relay.SkillSignal;

import java.util.Objects;

/**
 * First-class cognitive pathway for procedural skill crystallization and lifecycle management (ADR-0086 §5.2).
 */
public final class SkillPathway extends AbstractPathway<SkillSignal, SkillReport> implements AutoCloseable {

    @Override
    public void close() {
        // No native off-heap or thread pool resources to close in pathway shell
    }

    public SkillPathway(final SkillRecipe recipe) {
        super("skill", SkillSignal.class, SkillReport.class);
        Objects.requireNonNull(recipe, "recipe cannot be null");
        final PathwayComposer<SkillSignal> composer = PathwayComposer.of("skill");
        recipe.compose(composer);
        initEngine(composer.build());
    }

    public SkillPathway(final PathwayEngine<SkillSignal> engine) {
        super("skill", SkillSignal.class, SkillReport.class, engine);
    }

    /**
     * Creates a standard instance wired with all 6 procedural crystallization relays:
     * admission, extraction, deduplication, persistence, lineage tracking, and utility updating.
     * When running without a catalog-registered {@code RememberPathway}, persistence degrades
     * gracefully without failing the pathway execution.
     *
     * @return configured SkillPathway
     */
    public static SkillPathway standard() {
        final SkillRecipe recipe = SkillRecipe.builder().build();
        return new SkillPathway(recipe);
    }

    /**
     * Conducts the skill signal through the pathway relays and returns the compilation report.
     *
     * @param signal mutable skill signal
     * @return resulting skill report
     */
    public SkillReport compile(final SkillSignal signal) {
        return conduct(signal);
    }

    @Override
    protected SkillReport project(final SkillSignal signal) {
        return new SkillReport(
                signal.persistedSkillId(),
                signal.parents(),
                signal.duplicateOf(),
                signal.extractedBody(),
                signal.mode(),
                signal.mode() == SkillSignal.Mode.REINFORCE && signal.commit()
        );
    }
}
