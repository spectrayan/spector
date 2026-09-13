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
package com.spectrayan.spector.memory.valhalla;

import com.spectrayan.spector.commons.valhalla.ValueClassValidator;
import com.spectrayan.spector.memory.model.CognitiveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class MemoryValhallaReadinessTest {

    @Test
    @DisplayName("Spector Memory value candidates pass JEP 390 / JEP 401 compliance audit")
    void testMemoryValueCandidates() {
        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(CognitiveResult.class))
                .doesNotThrowAnyException();
    }
}
