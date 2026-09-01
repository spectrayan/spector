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
package com.spectrayan.spector.memory.model;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Map;

/**
 * Container representation of the self-model JSON payload stored inside the INSULA region.
 */
public record InsulaSelfModel(
        @JsonProperty("type") String type,
        @JsonProperty("soul") SoulContext soul,
        @JsonProperty("salience") SalienceProfile salience,
        @JsonProperty("metadata") Map<String, Object> metadata
) {
    public InsulaSelfModel {
        metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Map.of();
    }
}
