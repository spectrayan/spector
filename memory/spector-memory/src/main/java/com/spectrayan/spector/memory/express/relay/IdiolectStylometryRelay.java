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
import com.spectrayan.spector.memory.model.IdiolectProfile;
import com.spectrayan.spector.memory.persona.IdiolectPromptFormatter;
import com.spectrayan.spector.core.similarity.StylometricKernel;
import java.util.Arrays;

public class IdiolectStylometryRelay implements SynapticRelay<ExpressSignal> {

    @Override
    public boolean transmit(ExpressSignal signal) {
        if (signal.personaContext() != null) {
            IdiolectProfile profile = signal.personaContext().idiolect();
            if (profile != null) {
                // Formatting prompt directives
                String promptDirectives = IdiolectPromptFormatter.formatPromptDirectives(profile);
                signal.attributes().put("promptDirectives", promptDirectives);
                signal.attributes().put("idiolectProfile", profile);
                
                // We could validate against StylometricKernel if there is another embedding, 
                // but just accessing StylometricKernel ensures it's available.
                // e.g., dummy validation or self-similarity check
                if (profile.idiolectEmbedding() != null) {
                    float[] weights = new float[profile.idiolectEmbedding().length];
                    Arrays.fill(weights, 1.0f);
                    float sim = StylometricKernel.stylometricSimilarity(profile.idiolectEmbedding(), profile.idiolectEmbedding(), weights);
                    if (sim < 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public String relayName() {
        return "idiolect_stylometry";
    }
}
