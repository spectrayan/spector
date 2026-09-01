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
package com.spectrayan.spector.memory.aisme.workspace;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AttentionSchema}.
 */
class AttentionSchemaTest {

    @Test
    void defaultSchema_hasValidParameters() {
        AttentionSchema schema = AttentionSchema.defaultSchema();
        assertThat(schema.dominantFocus()).isEqualTo("GENERAL_ASSOCIATION");
        assertThat(schema.focusPrecision()).isEqualTo(1.0f);
        assertThat(schema.descriptiveRationale()).isNotEmpty();
    }

    @Test
    void invalidPrecision_throwsValidationException() {
        assertThatThrownBy(() -> new AttentionSchema("FOCUS", -1.0f, 0L, ""))
                .isInstanceOf(SpectorValidationException.class);
    }
}
