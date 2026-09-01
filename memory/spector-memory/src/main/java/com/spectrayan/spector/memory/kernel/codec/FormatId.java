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
package com.spectrayan.spector.memory.kernel.codec;

import com.spectrayan.spector.memory.kernel.MemoryHeader;

/**
 * Identifies a concrete on-disk format: a 4-byte magic plus a version
 * interpreted in that magic's own numbering scheme.
 */
public record FormatId(int magic, int version) {

    public static FormatId smkm(int schemaVersion) {
        return new FormatId(MemoryHeader.MAGIC, schemaVersion);
    }

    public boolean isSmkm() {
        return magic == MemoryHeader.MAGIC;
    }

    @Override
    public String toString() {
        return magicAscii(magic) + " v" + version;
    }

    private static String magicAscii(int magic) {
        byte b1 = (byte) ((magic >> 24) & 0xFF);
        byte b2 = (byte) ((magic >> 16) & 0xFF);
        byte b3 = (byte) ((magic >> 8) & 0xFF);
        byte b4 = (byte) (magic & 0xFF);
        return new String(new byte[]{b1, b2, b3, b4}, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
