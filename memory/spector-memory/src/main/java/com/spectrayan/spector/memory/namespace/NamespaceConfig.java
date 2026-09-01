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
package com.spectrayan.spector.memory.namespace;

/**
 * Configuration for a single memory namespace.
 *
 * <h3>Design</h3>
 * <p>Each namespace represents an isolated memory space — give it a directory,
 * it stores memories there. Core does not know or care about tenants,
 * organizations, or multi-tenancy. That's the enterprise layer's job.</p>
 *
 * <h3>Extension Point</h3>
 * <p>Enterprise layers can extend this record's semantics by wrapping it
 * with tenant metadata externally. Core only needs {@code id} and quotas.</p>
 *
 * @param id              unique namespace identifier (e.g., "agent-alpha")
 * @param displayName     human-readable name
 * @param maxMemories     maximum total memories across all tiers (-1 = unlimited)
 * @param maxPartitions   maximum partition count (-1 = unlimited)
 * @param maxStorageBytes maximum disk storage in bytes (-1 = unlimited)
 * @param readOnly        if true, namespace is frozen (no ingestion)
 */
public record NamespaceConfig(
        String id,
        String displayName,
        long maxMemories,
        int maxPartitions,
        long maxStorageBytes,
        boolean readOnly
) {

    /** Default configuration: no quotas, read-write. */
    public static final NamespaceConfig DEFAULT =
            new NamespaceConfig("default", "Default Namespace", -1, -1, -1, false);

    /** Maximum characters in a namespace ID. */
    public static final int MAX_ID_LENGTH = 63;

    /**
     * Creates a config with the given ID and unlimited quotas.
     */
    public static NamespaceConfig unlimited(String id) {
        return new NamespaceConfig(id, id, -1, -1, -1, false);
    }

    /**
     * Creates a config with the given quotas.
     */
    public static NamespaceConfig withQuotas(String id, long maxMemories,
                                              int maxPartitions, long maxStorageBytes) {
        return new NamespaceConfig(id, id, maxMemories, maxPartitions, maxStorageBytes, false);
    }

    /**
     * Validates the namespace ID format.
     *
     * <p>Namespace IDs must be non-null, non-empty, at most {@link #MAX_ID_LENGTH}
     * characters, start with alphanumeric, and contain only alphanumeric characters,
     * hyphens, and underscores. Dots, slashes, and backslashes are forbidden
     * to prevent path traversal.</p>
     *
     * @return true if the ID is valid
     */
    public boolean isValidId() {
        return isValidSegment(id);
    }

    /**
     * Validates a single path segment (e.g., namespace ID).
     *
     * <p>Must be 1-63 chars, start with alphanumeric, contain only
     * alphanumeric, hyphens, and underscores. No dots, slashes, or backslashes.</p>
     *
     * @param segment the ID to validate
     * @return true if the segment is valid
     */
    public static boolean isValidSegment(String segment) {
        if (segment == null || segment.isEmpty() || segment.length() > MAX_ID_LENGTH) return false;
        char first = segment.charAt(0);
        if (!Character.isLetterOrDigit(first)) return false;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') return false;
            if (c == '/' || c == '\\' || c == '.') return false;
        }
        return true;
    }
}
