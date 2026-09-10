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
package com.spectrayan.spector.memory.model.enactment;

import com.spectrayan.spector.kernel.api.MemoryType;

import java.util.List;

/**
 * Citation pointing to a specific engram justifying an enactment stance (ADR-0032).
 */
public record EngramCitation(
        String memoryId,
        MemoryType tier,
        List<String> tags,
        float score,
        boolean synthetic
) {
    public EngramCitation {
        tags = (tags != null) ? List.copyOf(tags) : List.of();
    }
}
