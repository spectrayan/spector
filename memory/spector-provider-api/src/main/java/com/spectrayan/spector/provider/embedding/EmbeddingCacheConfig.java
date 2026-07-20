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
package com.spectrayan.spector.provider.embedding;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the LRU embedding cache ({@link CachingEmbeddingProvider}).
 *
 * @param enabled          whether caching is enabled
 * @param maxSize          maximum number of cached embeddings (must be &gt; 0)
 * @param ttl              time-to-live for cached entries ({@link Duration#ZERO} = no expiry)
 * @param statsLogInterval how often cache statistics are logged at INFO level
 *                         ({@link Duration#ZERO} = never)
 */
public record EmbeddingCacheConfig(
        boolean enabled,
        int maxSize,
        Duration ttl,
        Duration statsLogInterval
) {

    /** Default configuration: enabled, 1000 entries, 1 hour TTL, stats logged every 5 minutes. */
    public static final EmbeddingCacheConfig DEFAULT =
            new EmbeddingCacheConfig(true, 1000, Duration.ofMinutes(60), Duration.ofMinutes(5));

    public EmbeddingCacheConfig {
        Objects.requireNonNull(ttl, "ttl must not be null");
        Objects.requireNonNull(statsLogInterval, "statsLogInterval must not be null");
        if (maxSize <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "maxSize", 1, Integer.MAX_VALUE, maxSize);
        }
        if (ttl.isNegative()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NEGATIVE, "ttl", ttl.toMillis());
        }
        if (statsLogInterval.isNegative()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NEGATIVE, "statsLogInterval", statsLogInterval.toMillis());
        }
    }

    /**
     * Creates a disabled cache configuration.
     */
    public static EmbeddingCacheConfig disabled() {
        return new EmbeddingCacheConfig(false, 1, Duration.ZERO, Duration.ZERO);
    }

    /**
     * Returns {@code true} if cached entries expire after {@link #ttl()}.
     */
    public boolean ttlEnabled() {
        return !ttl.isZero();
    }
}
