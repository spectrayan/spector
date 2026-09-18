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
package com.spectrayan.spector.memory.pathway.wander.relay;

import com.spectrayan.spector.commons.pathway.Specification;

/**
 * Predicate specifications guarding execution of {@link com.spectrayan.spector.commons.pathway.SynapticRelay}
 * stages within the {@link com.spectrayan.spector.memory.pathway.wander.WanderPathway}.
 *
 * @since 1.2.0
 */
public final class WanderGates {

    private WanderGates() {}

    /**
     * Gate evaluating whether the cognitive system has been idle for the required duration.
     */
    public static final Specification<WanderSignal> IS_IDLE =
            Specification.of("system is not idle (activity within threshold)",
                    s -> (System.currentTimeMillis() - s.lastActivityTimestampMs()) >= (long) s.idleThresholdSeconds() * 1000L);

    /**
     * Gate evaluating whether Default Mode Network spontaneous mind-wandering is enabled.
     */
    public static final Specification<WanderSignal> DMN_ENABLED =
            Specification.of("DMN spontaneous activity is disabled in AISME configuration",
                    s -> s.aismeConfig() == null || (s.aismeConfig().enabled() && s.aismeConfig().enableDmnSpontaneous()));

    /**
     * Gate evaluating whether Riemannian manifold synergy evaluation is enabled.
     */
    public static final Specification<WanderSignal> MANIFOLD_ENABLED =
            Specification.of("Riemannian cognitive manifold is disabled in AISME configuration",
                    s -> s.aismeConfig() != null && s.aismeConfig().enabled() && s.aismeConfig().enableManifold());

    /**
     * Gate evaluating whether longitudinal consciousness continuity (\(\Phi_{CC}\)) tracking is enabled.
     */
    public static final Specification<WanderSignal> CONTINUITY_ENABLED =
            Specification.of("Longitudinal continuity tracking is disabled or memory uninitialized",
                    s -> s.continuityMemory() != null && (s.aismeConfig() == null || (s.aismeConfig().enabled() && s.aismeConfig().enableLongitudinalContinuity())));
}
