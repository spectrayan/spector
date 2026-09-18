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
package com.spectrayan.spector.memory.index;

/**
 * Classifies why a hypergraph hyperedge was quarantined by the {@link IndexReconcileEngine} (ADR-0082 Phase 2.1).
 *
 * @since 1.1.0
 */
public enum QuarantineReason {

    /** A vertex in the hyperedge references an entityId that is out of bounds or not interned in EntityDirectory. */
    DANGLING_ENTITY,

    /** The hyperedge's memoryIdx does not correspond to any live memory slot in the MemoryIndex. */
    DANGLING_MEMORY,

    /** All vertices in the hyperedge reference dangling entities — the edge is fully orphaned. */
    ORPHANED_HYPEREDGE
}
