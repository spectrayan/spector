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
package com.spectrayan.spector.memory.express.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.model.ProsodyParameterVector;
import com.spectrayan.spector.memory.model.VocalProsodyDNA;
import com.spectrayan.spector.memory.persona.VocalProsodyTransferEngine;

public class VocalProsodyRelay implements SynapticRelay<ExpressSignal> {

    @Override
    public boolean transmit(ExpressSignal signal) {
        if (signal.interoceptiveState() != null) {
            VocalProsodyDNA dna = (signal.personaContext() != null && signal.personaContext().vocalProsody() != null)
                    ? signal.personaContext().vocalProsody()
                    : VocalProsodyDNA.NEUTRAL;

            ProsodyParameterVector prosodyVector = VocalProsodyTransferEngine.compute(dna, signal.interoceptiveState());
            if (prosodyVector != null) {
                signal.attributes().put("prosodyVector", prosodyVector);
            }
        }
        return true;
    }

    @Override
    public String relayName() {
        return "vocal_prosody";
    }
}
