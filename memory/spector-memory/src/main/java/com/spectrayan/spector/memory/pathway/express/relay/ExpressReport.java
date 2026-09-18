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

import com.spectrayan.spector.commons.pathway.ConductionOutcome;
import com.spectrayan.spector.memory.model.BlendshapeVector;
import com.spectrayan.spector.memory.model.PhenomenologicalContextPack;
import com.spectrayan.spector.memory.model.IdiolectProfile;
import com.spectrayan.spector.memory.model.ProsodyParameterVector;
import java.time.Duration;

public record ExpressReport(
    ProsodyParameterVector prosodyVector,
    BlendshapeVector blendshapeVector,
    IdiolectProfile idiolectProfile,
    PhenomenologicalContextPack contextPack,
    String promptDirectives,
    String internalMonologue,
    String ssmlTags,
    Duration elapsed,
    int relaysExecuted,
    ConductionOutcome outcome
) {
    public ExpressReport(
        ProsodyParameterVector prosodyVector,
        BlendshapeVector blendshapeVector,
        IdiolectProfile idiolectProfile,
        PhenomenologicalContextPack contextPack,
        String promptDirectives,
        String internalMonologue,
        String ssmlTags,
        Duration elapsed,
        int relaysExecuted
    ) {
        this(prosodyVector, blendshapeVector, idiolectProfile, contextPack, promptDirectives, internalMonologue, ssmlTags, elapsed, relaysExecuted, null);
    }

    public static ExpressReport empty() {
        return new ExpressReport(null, null, null, null, "", "", "", Duration.ZERO, 0, null);
    }

    public static ExpressReport empty(ConductionOutcome outcome) {
        return new ExpressReport(null, null, null, null, "", "", "", Duration.ZERO, 0, outcome);
    }
}
