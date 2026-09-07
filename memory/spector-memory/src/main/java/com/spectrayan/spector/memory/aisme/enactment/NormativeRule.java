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
