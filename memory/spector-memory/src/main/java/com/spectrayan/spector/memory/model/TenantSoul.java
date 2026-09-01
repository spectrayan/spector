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

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Tenant-level soul/identity context.
 */
public record TenantSoul(
        String id,
        String name,
        String description,
        List<String> domainFocus,
        List<String> complianceRules,
        float[] identityEmbedding,
        short soulVersion,
        Instant createdAt,
        Instant updatedAt
) implements SoulContext {
    public TenantSoul {
        domainFocus = domainFocus != null ? Collections.unmodifiableList(domainFocus) : List.of();
        complianceRules = complianceRules != null ? Collections.unmodifiableList(complianceRules) : List.of();
    }
}
