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
package com.spectrayan.spector.memory.model;

import java.time.Instant;

/**
 * User-level soul/identity context wrapping the PersonaContext.
 */
public record UserSoul(
        String id,
        String name,
        String description,
        PersonaContext persona,
        float[] identityEmbedding,
        short soulVersion,
        Instant createdAt,
        Instant updatedAt
) implements SoulContext {

    @com.fasterxml.jackson.annotation.JsonCreator
    public UserSoul(
            @com.fasterxml.jackson.annotation.JsonProperty("id") String id,
            @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
            @com.fasterxml.jackson.annotation.JsonProperty("description") String description,
            @com.fasterxml.jackson.annotation.JsonProperty("persona") PersonaContext persona,
            @com.fasterxml.jackson.annotation.JsonProperty("identityEmbedding") float[] identityEmbedding,
            @com.fasterxml.jackson.annotation.JsonProperty("soulVersion") short soulVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("createdAt") Instant createdAt,
            @com.fasterxml.jackson.annotation.JsonProperty("updatedAt") Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.persona = persona;
        this.identityEmbedding = identityEmbedding;
        this.soulVersion = soulVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Backward-compatible constructor (version=0, no timestamps). */
    public UserSoul(String id, String name, String description,
                    PersonaContext persona, float[] identityEmbedding) {
        this(id, name, description, persona, identityEmbedding,
                (short) 0, null, null);
    }

    @Override
    public float[] identityEmbedding() {
        if (identityEmbedding != null) {
            return identityEmbedding;
        }
        return persona != null ? persona.aboutEmbedding() : null;
    }
}
