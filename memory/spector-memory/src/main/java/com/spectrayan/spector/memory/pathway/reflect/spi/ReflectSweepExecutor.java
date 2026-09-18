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
package com.spectrayan.spector.memory.pathway.reflect.spi;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepProgress;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;

/**
 * Service Provider Interface for orchestrating reflection sweeps across memory partitions.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}. Higher-priority
 * implementations (such as Spring Batch in Synapse) supersede the default in-process executor.</p>
 *
 * @since 1.5.0
 */
public interface ReflectSweepExecutor {

    /**
     * Unique name identifying this sweep executor (e.g., "in-process", "spring-batch", "quartz").
     */
    String name();

    /**
     * Selection priority. Higher values take precedence when multiple executors are available.
     * <ul>
     *   <li>{@code 0}: Default in-process executor</li>
     *   <li>{@code 100}: Spring Batch orchestrator in Synapse</li>
     * </ul>
     */
    int priority();

    /**
     * Returns true if the runtime dependencies and environment for this executor are available.
     */
    boolean available();

    /**
     * Executes a reflection consolidation sweep according to the provided specification.
     *
     * @param memory the target memory instance
     * @param spec   the sweep configuration and filter specification
     * @return summary report of the sweep execution
     */
    ReflectReport execute(SpectorMemory memory, ReflectSweepSpec spec);

    /**
     * Returns the current progress snapshot for an active or completed sweep.
     *
     * @param sweepId unique sweep identifier
     * @return progress telemetry, or null if unknown
     */
    ReflectSweepProgress progress(String sweepId);
}
