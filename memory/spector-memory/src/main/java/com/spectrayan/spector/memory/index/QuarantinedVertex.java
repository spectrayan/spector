/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.index;

/**
 * Immutable snapshot of a quarantined hypergraph hyperedge (ADR-0082 Phase 2.1).
 *
 * <p>Captured by the {@link QuarantineRegistry} when the {@link IndexReconcileEngine}
 * detects referential integrity violations in {@code HyperEntityGraphMemory}.</p>
 *
 * @param edgeId        the hyperedge ID that was quarantined
 * @param reason        classification of the integrity violation
 * @param detectedAtMs  epoch-millis timestamp when the violation was first detected
 *
 * @since 1.1.0
 */
public record QuarantinedVertex(
        int edgeId,
        QuarantineReason reason,
        long detectedAtMs
) {}
