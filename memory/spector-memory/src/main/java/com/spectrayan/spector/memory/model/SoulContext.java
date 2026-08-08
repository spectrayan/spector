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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic soul context representing the self-model identity across different scopes.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TenantSoul.class, name = "TENANT"),
    @JsonSubTypes.Type(value = OrgUnitSoul.class, name = "ORG_UNIT"),
    @JsonSubTypes.Type(value = AgentSoul.class, name = "AGENT"),
    @JsonSubTypes.Type(value = UserSoul.class, name = "USER")
})
public sealed interface SoulContext permits TenantSoul, OrgUnitSoul, AgentSoul, UserSoul {
    /**
     * Unique identifier for this soul context.
     *
     * @return the unique soul ID
     */
    String id();

    /**
     * Display name of the identity.
     *
     * @return the identity name
     */
    String name();

    /**
     * Brief description of the identity's role, expertise, or purpose.
     *
     * @return the description, or null if not provided
     */
    String description();

    /**
     * Pre-computed embedding vector representing the core purpose or definition of this identity.
     * Used for relevance checking against input topics.
     *
     * @return the purpose embedding vector, or null if not computed
     */
    float[] identityEmbedding();
}
