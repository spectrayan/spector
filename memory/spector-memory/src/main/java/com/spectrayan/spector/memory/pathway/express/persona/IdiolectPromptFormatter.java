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
package com.spectrayan.spector.memory.pathway.express.persona;

import com.spectrayan.spector.memory.model.IdiolectProfile;

public class IdiolectPromptFormatter {
    public static String formatPromptDirectives(IdiolectProfile profile) {
        if (profile == null || !profile.isPresent()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### Idiolect Directives\n");
        sb.append("- Sentence Cadence: Mean length ~").append(profile.stylometrics().meanSentenceLength()).append(" words, formality score: ").append(profile.stylometrics().formalityScore()).append("\n");

        if (profile.lexicon().hasCatchphrases()) {
            sb.append("- Catchphrases: ").append(String.join(", ", profile.lexicon().catchphrases())).append("\n");
        }
        
        sb.append("- Rhetorical Tone: ").append(profile.rhetoric().directness()).append("\n");
        sb.append("- Humor Style: ").append(profile.rhetoric().humor()).append("\n");

        if (profile.lexicon().hasTabooWords()) {
            sb.append("- Taboo Words (Avoid): ").append(String.join(", ", profile.lexicon().tabooWords())).append("\n");
        }

        return sb.toString();
    }
}
