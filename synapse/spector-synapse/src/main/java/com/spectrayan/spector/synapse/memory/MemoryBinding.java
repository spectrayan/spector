/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Immutable per-request memory binding context holding the resolved {@link SpectorMemory}
 * instance along with its owning account and resolved namespace identifier (ADR-0029 §16).
 *
 * @param memory      the active SpectorMemory engine
 * @param accountId   the authenticated account identifier
 * @param namespaceId the resolved data-plane namespace TSID
 * @param slug        the requested or resolved namespace slug
 */
public record MemoryBinding(
        SpectorMemory memory,
        String accountId,
        String namespaceId,
        String slug) {

    /** RequestAttributes attribute key for the bound context. */
    public static final String ATTRIBUTE_KEY = "com.spectrayan.spector.synapse.memory.MemoryBinding";
}
