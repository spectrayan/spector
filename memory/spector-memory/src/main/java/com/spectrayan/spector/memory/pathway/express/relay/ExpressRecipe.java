/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway.express.relay;

import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

/**
 * Declarative recipe for composing the Express (embodied expression) cognitive pathway.
 *
 * <p>Every stage is {@link ErrorPolicy#DEGRADE_GRACEFULLY} with no resilience decorator, per
 * the ADR-0036 §14 "Decide / Wander / Express" row — all four relays are local computation
 * over the signal, so there is no remote call to budget or isolate.</p>
 */
public class ExpressRecipe implements PathwayRecipe<ExpressSignal> {

    @Override
    public void compose(final PathwayComposer<ExpressSignal> composer) {
        composer.gated(RelayNames.IDIOLECT_STYLOMETRY, ExpressGates.IDIOLECT_ENABLED, idiolectStylometry(), ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.VOCAL_PROSODY, ExpressGates.PROSODY_ENABLED, vocalProsody(), ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.EMBODIED_KINESICS, ExpressGates.KINESICS_ENABLED, embodiedKinesics(), ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.PHENOMENOLOGICAL_STREAM, ExpressGates.PHENOMENOLOGICAL_ENABLED, phenomenologicalStream(), ErrorPolicy.DEGRADE_GRACEFULLY);
    }

    /** Returns the idiolect stylometry relay. */
    protected SynapticRelay<ExpressSignal> idiolectStylometry() {
        return new IdiolectStylometryRelay();
    }

    /** Returns the vocal prosody relay. */
    protected SynapticRelay<ExpressSignal> vocalProsody() {
        return new VocalProsodyRelay();
    }

    /** Returns the embodied kinesics relay. */
    protected SynapticRelay<ExpressSignal> embodiedKinesics() {
        return new EmbodiedKinesicsRelay();
    }

    /** Returns the phenomenological stream relay. */
    protected SynapticRelay<ExpressSignal> phenomenologicalStream() {
        return new PhenomenologicalStreamRelay();
    }
}
