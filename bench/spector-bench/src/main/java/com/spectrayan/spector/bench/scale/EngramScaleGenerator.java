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
package com.spectrayan.spector.bench.scale;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;

import java.time.Instant;
import java.util.Random;

/**
 * Deterministic synthetic engram generator for scale benchmarks.
 *
 * <p>Produces synthetic engrams with realistic temporal spread, synaptic bloom tags,
 * semantic diversity, and cognitive metadata attributes (importance, valence).
 */
public final class EngramScaleGenerator {

    private static final String[] TOPICS = {
            "database connection pool tuning",
            "microservice circuit breaker tripping",
            "kafka consumer rebalance latency",
            "redis cache invalidation storm",
            "distributed transaction two-phase commit",
            "panama off-heap memory arena alignment",
            "vector index IVF centroid clustering",
            "hebbian synaptic co-activation reinforcement",
            "neural manifold trajectory projection",
            "biological sleep consolidation replay"
    };

    private static final String[] DOMAINS = {
            "kernel", "network", "storage", "cortex", "synapse", "metrics", "security", "runtime"
    };

    private final Random rng;
    private final long baseTimestampMs;
    private final long stepIntervalMs;

    public EngramScaleGenerator(long seed, long baseTimestampMs, long stepIntervalMs) {
        this.rng = new Random(seed);
        this.baseTimestampMs = baseTimestampMs;
        this.stepIntervalMs = stepIntervalMs;
    }

    public EngramScaleGenerator(long seed) {
        // Default: base timestamp 30 days ago, step interval 10 seconds per memory
        this(seed, Instant.now().minus(java.time.Duration.ofDays(30)).toEpochMilli(), 10_000L);
    }

    /**
     * Synthetic engram data model.
     */
    public record GeneratedEngram(
            String id,
            String text,
            MemoryType type,
            MemorySource source,
            long timestampMs,
            float importance,
            byte valence,
            String[] tags
    ) {}

    /**
     * Generates the synthetic engram at the specified index.
     */
    public GeneratedEngram generate(long index) {
        String id = String.format("scale-engram-%08d", index);
        String topic = TOPICS[(int) Math.floorMod(index, TOPICS.length)];
        String domain = DOMAINS[(int) Math.floorMod(index / 7, DOMAINS.length)];
        String text = String.format("%s in %s zone-%d iteration-%d", topic, domain, (int) Math.floorMod(index, 16), index);

        long timestampMs = baseTimestampMs + (index * stepIntervalMs);
        float importance = 1.0f + (rng.nextFloat() * 9.0f);
        byte valence = (byte) (rng.nextInt(160) - 80);

        String[] tags = new String[] {
                domain,
                "tier-" + (Math.floorMod(index, 3) == 0 ? "episodic" : "semantic"),
                "cluster-" + Math.floorMod(index, 10),
                "scale"
        };

        MemoryType type = MemoryType.SEMANTIC;
        MemorySource source = MemorySource.USER_STATED;

        return new GeneratedEngram(id, text, type, source, timestampMs, importance, valence, tags);
    }

    public GeneratedEngram generate(int index) {
        return generate((long) index);
    }

    public long getBaseTimestampMs() {
        return baseTimestampMs;
    }

    public long getStepIntervalMs() {
        return stepIntervalMs;
    }
}
