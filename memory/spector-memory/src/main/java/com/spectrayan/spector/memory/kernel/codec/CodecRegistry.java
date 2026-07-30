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
package com.spectrayan.spector.memory.kernel.codec;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Global registry mapping layout IDs and legacy magics to owning Codec instances.
 */
public final class CodecRegistry {

    private final Map<Integer, Codec<?>> byLayoutId;
    private final Map<Integer, Codec<?>> byLegacyMagic;

    private CodecRegistry(Map<Integer, Codec<?>> byLayoutId, Map<Integer, Codec<?>> byLegacyMagic) {
        this.byLayoutId = Map.copyOf(byLayoutId);
        this.byLegacyMagic = Map.copyOf(byLegacyMagic);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Codec<?>> byLayoutId(int layoutId) {
        return Optional.ofNullable(byLayoutId.get(layoutId));
    }

    public Optional<Codec<?>> byLegacyMagic(int magic) {
        return Optional.ofNullable(byLegacyMagic.get(magic));
    }

    public static final class Builder {
        private final Map<Integer, Codec<?>> byLayoutId = new HashMap<>();
        private final Map<Integer, Codec<?>> byLegacyMagic = new HashMap<>();

        public Builder register(Codec<?> codec) {
            int layoutId = codec.layout().layoutId();
            if (byLayoutId.put(layoutId, codec) != null) {
                throw new IllegalArgumentException("Duplicate codec registration for layoutId: " + layoutId);
            }
            for (int magic : codec.legacyMagics()) {
                byLegacyMagic.put(magic, codec);
            }
            return this;
        }

        public CodecRegistry build() {
            return new CodecRegistry(byLayoutId, byLegacyMagic);
        }
    }
}
