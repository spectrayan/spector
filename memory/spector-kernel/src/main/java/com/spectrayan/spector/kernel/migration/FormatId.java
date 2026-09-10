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
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.kernel.region.RegionPreamble;

/**
 * Identifies a concrete on-disk format: a 4-byte magic plus a version
 * interpreted in that magic's own numbering scheme.
 */
public record FormatId(int magic, int version) {

    public static FormatId smkm(int schemaVersion) {
        return new FormatId(RegionPreamble.MAGIC, schemaVersion);
    }

    public boolean isSmkm() {
        return magic == RegionPreamble.MAGIC;
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
