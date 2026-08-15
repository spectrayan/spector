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
package com.spectrayan.spector.connector.spi;

import com.spectrayan.spector.connector.model.ExecutionRecord;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation of {@link ExecutionLogger} for testing.
 */
public class InMemoryExecutionLogger implements ExecutionLogger {

    private final List<ExecutionRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void log(ExecutionRecord record) {
        records.add(record);
    }

    @Override
    public List<ExecutionRecord> getHistory(String routeId, int limit) {
        return records.stream()
                .filter(r -> r.routeId().equals(routeId))
                .sorted(Comparator.comparing(ExecutionRecord::startedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<ExecutionRecord> getLatest(String routeId) {
        return records.stream()
                .filter(r -> r.routeId().equals(routeId))
                .max(Comparator.comparing(ExecutionRecord::startedAt));
    }

    /** Returns all records (for testing assertions). */
    public List<ExecutionRecord> allRecords() {
        return List.copyOf(records);
    }
}
