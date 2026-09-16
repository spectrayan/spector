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
/**
 * Provides the concrete off-heap stores of the memory kernel — the types that actually hold
 * engrams, associations, and audit trails in mmap'd regions.
 *
 * <p>Each store binds one content to one physical shape from
 * {@link com.spectrayan.spector.kernel.shape} and one {@code RegionLayout} from
 * {@link com.spectrayan.spector.kernel.layout}. Per the shape rule, a store names its
 * <em>content</em> only; the shape token lives on the abstraction it extends
 * ({@link AbstractRecordMemory}, {@link AbstractAppendMemory}, {@link AbstractGraphMemory},
 * {@link AbstractChainMemory}, {@link AbstractHashTableMemory}, {@link AbstractRegistryMemory}).
 * The D11 vocabulary rule applies to fields as well: {@code *Memory}, never {@code *Store}.</p>
 *
 * <h3>Cognitive tiers</h3>
 * <p>{@link SemanticMemory} (neocortex — consolidated facts with their quantized vectors),
 * {@link ProceduralMemory} (basal ganglia — crystallized behavioral policies),
 * {@link WorkingMemory} (prefrontal cortex — a small circular buffer of active items), and the
 * log-structured {@link EpisodicMemory} (conversation turns) all satisfy {@link EngramRegion},
 * unifying the four tiers without constraining stride or layout (ADR-0030).</p>
 *
 * <h3>Recall dynamics and association</h3>
 * <p>{@link StrengthMemory} owns the mutable half of the MF-001 header — decay, storage
 * strength, and recall counts — in one Recall Audit Region per partition bundle, deliberately
 * apart from the immutable encoding header (ADR-0028). Associations live in
 * {@link HebbianGraphMemory} (CSR-compressed co-recall edges),
 * {@link HyperEntityGraphMemory} (role-bearing hyperedges), {@link CoActivationMemory}
 * (synaptic tag co-occurrence and STDP), and {@link TemporalChainMemory}.</p>
 *
 * <h3>Provenance and identity</h3>
 * <p>{@link ProvenanceMemory} records which episodic turns produced which consolidated fact
 * (ADR-0029), {@link ContinuityMemory} tracks longitudinal identity cohesion,
 * {@link InsulaMemory} holds the self-model, and {@link DreamJournalMemory} keeps simulated
 * narratives on a strictly append-only trail so imagination cannot launder itself into belief.</p>
 *
 * <p>Key components include {@link EngramRegion}, {@link AbstractEngramMemory},
 * {@link SemanticMemory}, {@link EpisodicMemory}, {@link StrengthMemory},
 * {@link HebbianGraphMemory}, {@link ProvenanceMemory}, and {@link DefaultHeaderCursor}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.shape.AbstractAppendMemory;
import com.spectrayan.spector.kernel.shape.AbstractChainMemory;
import com.spectrayan.spector.kernel.shape.AbstractGraphMemory;
import com.spectrayan.spector.kernel.shape.AbstractHashTableMemory;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.kernel.shape.AbstractRegistryMemory;
