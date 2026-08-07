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

/**
 * User-level soul/identity context wrapping the PersonaContext.
 */
public record UserSoul(
        String id,
        String name,
        String description,
        PersonaContext persona,
        float[] identityEmbedding
) implements SoulContext {
    @Override
    public float[] identityEmbedding() {
        if (identityEmbedding != null) {
            return identityEmbedding;
        }
        return persona != null ? persona.aboutEmbedding() : null;
    }
}
