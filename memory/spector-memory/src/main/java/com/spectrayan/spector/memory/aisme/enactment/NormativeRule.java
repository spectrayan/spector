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
package com.spectrayan.spector.memory.aisme.enactment;

import java.util.Locale;
import java.util.Objects;

/**
 * Declares a normative evaluation rule matching an agent coreValue against problem text patterns (ADR-0032).
 */
public record NormativeRule(
        String coreValueKeyword,
        String textKeyword,
        String violationConcern
) {

    public NormativeRule {
        Objects.requireNonNull(coreValueKeyword, "coreValueKeyword must not be null");
        Objects.requireNonNull(textKeyword, "textKeyword must not be null");
        Objects.requireNonNull(violationConcern, "violationConcern must not be null");
        coreValueKeyword = coreValueKeyword.toLowerCase(Locale.ROOT);
        textKeyword = textKeyword.toLowerCase(Locale.ROOT);
    }

    public static NormativeRule of(String coreValueKeyword, String textKeyword, String violationConcern) {
        return new NormativeRule(coreValueKeyword, textKeyword, violationConcern);
    }
}
