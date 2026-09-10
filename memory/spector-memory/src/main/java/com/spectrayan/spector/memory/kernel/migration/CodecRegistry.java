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
package com.spectrayan.spector.memory.kernel.migration;

import com.spectrayan.spector.memory.kernel.migration.FormatCodec;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorInternalException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Global registry mapping layout IDs and legacy magics to owning Codec instances.
 */
public final class CodecRegistry {

    private final Map<Integer, FormatCodec<?>> byLayoutId;
    private final Map<Integer, FormatCodec<?>> byLegacyMagic;

    private CodecRegistry(Map<Integer, FormatCodec<?>> byLayoutId, Map<Integer, FormatCodec<?>> byLegacyMagic) {
        this.byLayoutId = Map.copyOf(byLayoutId);
        this.byLegacyMagic = Map.copyOf(byLegacyMagic);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<FormatCodec<?>> byLayoutId(int layoutId) {
        return Optional.ofNullable(byLayoutId.get(layoutId));
    }

    public Optional<FormatCodec<?>> byLegacyMagic(int magic) {
        return Optional.ofNullable(byLegacyMagic.get(magic));
    }

    public static final class Builder {
        private final Map<Integer, FormatCodec<?>> byLayoutId = new HashMap<>();
        private final Map<Integer, FormatCodec<?>> byLegacyMagic = new HashMap<>();

        public Builder register(FormatCodec<?> codec) {
            int layoutId = codec.layout().layoutId();
            if (byLayoutId.put(layoutId, codec) != null) {
                throw new SpectorInternalException(
                        ErrorCode.INVARIANT_VIOLATED, "duplicate codec registration for layoutId " + layoutId);
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
