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
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.kernel.migration.FormatCodec;

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
