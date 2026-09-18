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
package com.spectrayan.spector.memory.pathway.express.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.model.ProsodyParameterVector;
import com.spectrayan.spector.memory.model.VocalProsodyDNA;
import com.spectrayan.spector.memory.pathway.express.persona.VocalProsodyTransferEngine;

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
