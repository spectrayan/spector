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
package com.spectrayan.spector.memory.kernel;

import java.util.Objects;

/**
 * A unique identifier for a memory instance.
 * Provides a stable identity for logging, metrics, and referencing.
 *
 * @param namespace    The namespace or domain this memory belongs to.
 * @param memoryName   The unique name of the memory within the namespace.
 * @param partitionSeq The partition sequence number, defaulting to 0 for non-partitioned memories.
 */
public record MemoryId(String namespace, String memoryName, int partitionSeq) implements Comparable<MemoryId> {

    /**
     * Creates a new MemoryId for a non-partitioned memory.
     *
     * @param namespace  The namespace.
     * @param memoryName The memory name.
     * @return A new MemoryId with partitionSeq set to 0.
     */
    public static MemoryId of(String namespace, String memoryName) {
        return new MemoryId(namespace, memoryName, 0);
    }

    /**
     * Parses a string representation of a MemoryId.
     */
    public static MemoryId parse(String str) {
        if (str == null) return null;
        int hashIdx = str.indexOf('#');
        int partitionSeq = 0;
        String base = str;
        if (hashIdx >= 0) {
            partitionSeq = Integer.parseInt(str.substring(hashIdx + 1));
            base = str.substring(0, hashIdx);
        }
        int slashIdx = base.indexOf('/');
        if (slashIdx >= 0) {
            return new MemoryId(base.substring(0, slashIdx), base.substring(slashIdx + 1), partitionSeq);
        } else {
            return new MemoryId("default", base, partitionSeq);
        }
    }

    public MemoryId {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(memoryName, "memoryName cannot be null");
        if (partitionSeq < 0) {
            throw new IllegalArgumentException("partitionSeq cannot be negative");
        }
    }

    @Override
    public String toString() {
        if (partitionSeq > 0) {
            return namespace + "/" + memoryName + "#" + partitionSeq;
        } else {
            return namespace + "/" + memoryName;
        }
    }

    @Override
    public int compareTo(MemoryId o) {
        int nsCompare = this.namespace.compareTo(o.namespace);
        if (nsCompare != 0) {
            return nsCompare;
        }
        int nameCompare = this.memoryName.compareTo(o.memoryName);
        if (nameCompare != 0) {
            return nameCompare;
        }
        return Integer.compare(this.partitionSeq, o.partitionSeq);
    }
}
