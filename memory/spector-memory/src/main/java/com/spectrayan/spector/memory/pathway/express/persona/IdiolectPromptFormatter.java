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
