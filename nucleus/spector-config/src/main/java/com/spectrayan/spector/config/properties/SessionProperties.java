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

import static com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_SESSION_BUFFER_SIZE;
import static com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_SESSION_BUFFER_TTL_MS;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration properties POJO for Memory Session buffering.
 */
public class SessionProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private int bufferSize = DEFAULT_MEMORY_SESSION_BUFFER_SIZE;
    private long bufferTtlMs = DEFAULT_MEMORY_SESSION_BUFFER_TTL_MS;

    public SessionProperties() {}

    public SessionProperties(int bufferSize, long bufferTtlMs) {
        this.bufferSize = bufferSize;
        this.bufferTtlMs = bufferTtlMs;
    }

    public int getBufferSize() { return bufferSize; }
    public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }

    public long getBufferTtlMs() { return bufferTtlMs; }
    public void setBufferTtlMs(long bufferTtlMs) { this.bufferTtlMs = bufferTtlMs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionProperties that = (SessionProperties) o;
        return bufferSize == that.bufferSize && bufferTtlMs == that.bufferTtlMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bufferSize, bufferTtlMs);
    }

    public SessionProperties copy() {
        return new SessionProperties(bufferSize, bufferTtlMs);
    }
}
