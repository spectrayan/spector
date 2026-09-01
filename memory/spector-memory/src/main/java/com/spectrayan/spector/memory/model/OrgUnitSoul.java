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

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Organizational Unit-level soul/identity context.
 */
public record OrgUnitSoul(
        String id,
        String name,
        String description,
        List<String> expertise,
        float[] identityEmbedding,
        short soulVersion,
        Instant createdAt,
        Instant updatedAt
) implements SoulContext {
    public OrgUnitSoul {
        expertise = expertise != null ? Collections.unmodifiableList(expertise) : List.of();
    }
}
