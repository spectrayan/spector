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
package com.spectrayan.spector.memory.pathway.reflect.spi.local;

import com.spectrayan.spector.memory.pathway.reflect.ReflectCheckpoint;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectCheckpointStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory checkpoint store for testing and in-memory persistence mode.
 *
 * @since 1.5.0
 */
public final class InMemoryReflectCheckpointStore implements ReflectCheckpointStore {

    private final Map<String, ReflectCheckpoint> checkpoints = new ConcurrentHashMap<>();

    @Override
    public Optional<ReflectCheckpoint> load(String sweepId) {
        if (sweepId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(checkpoints.get(sweepId));
    }

    @Override
    public void save(ReflectCheckpoint checkpoint) {
        if (checkpoint != null && checkpoint.sweepId() != null) {
            checkpoints.put(checkpoint.sweepId(), checkpoint);
        }
    }

    @Override
    public void delete(String sweepId) {
        if (sweepId != null) {
            checkpoints.remove(sweepId);
        }
    }

    public void clear() {
        checkpoints.clear();
    }
}
