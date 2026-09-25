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
package com.spectrayan.spector.metrics.observation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer counters recording recall partition fan-out, pruning, and budget capping (R3, F10).
 */
public final class RecallBudgetMetrics {

    public static final String METRIC_PARTITIONS_VISITED = "spector.recall.partitions_visited";
    public static final String METRIC_PARTITIONS_SKIPPED = "spector.recall.partitions_skipped";
    public static final String METRIC_PARTITIONS_BUDGETED = "spector.recall.partitions_budgeted";
    public static final String TAG_NAMESPACE = "spector.namespace";

    private RecallBudgetMetrics() {}

    /**
     * Records recall partition statistics to the provided meter registry.
     *
     * @param registry  Micrometer registry
     * @param namespace target namespace
     * @param visited   number of partitions visited/scanned
     * @param skipped   number of partitions pruned/skipped
     * @param budgeted  number of candidate partitions dropped by visit budget
     */
    public static void record(final MeterRegistry registry, final String namespace, final int visited, final int skipped, final int budgeted) {
        if (registry == null) {
            return;
        }
        final String ns = namespace != null && !namespace.isBlank() ? namespace : "default";

        final Counter visitedCounter = Counter.builder(METRIC_PARTITIONS_VISITED)
                .tag(TAG_NAMESPACE, ns)
                .description("Partitions visited during cognitive recall scan")
                .register(registry);
        if (visited > 0) {
            visitedCounter.increment(visited);
        }

        final Counter skippedCounter = Counter.builder(METRIC_PARTITIONS_SKIPPED)
                .tag(TAG_NAMESPACE, ns)
                .description("Partitions skipped by cognitive pruner during recall")
                .register(registry);
        if (skipped > 0) {
            skippedCounter.increment(skipped);
        }

        final Counter budgetedCounter = Counter.builder(METRIC_PARTITIONS_BUDGETED)
                .tag(TAG_NAMESPACE, ns)
                .description("Candidate partitions dropped due to visit budget capping")
                .register(registry);
        if (budgeted > 0) {
            budgetedCounter.increment(budgeted);
        }
    }
}
