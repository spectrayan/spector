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
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_WAL_MAX_CHUNK_BYTES;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration properties POJO for Memory Write-Ahead Logging (WAL).
 */
public class WalProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private long maxChunkBytes = DEFAULT_MEMORY_WAL_MAX_CHUNK_BYTES;

    public WalProperties() {}

    public WalProperties(long maxChunkBytes) {
        this.maxChunkBytes = maxChunkBytes;
    }

    public long getMaxChunkBytes() { return maxChunkBytes; }
    public void setMaxChunkBytes(long maxChunkBytes) { this.maxChunkBytes = maxChunkBytes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WalProperties that = (WalProperties) o;
        return maxChunkBytes == that.maxChunkBytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxChunkBytes);
    }

    public WalProperties copy() {
        return new WalProperties(maxChunkBytes);
    }
}
