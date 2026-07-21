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
package com.spectrayan.spector.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the persisted data-layout version for a Spector Memory data root.
 *
 * <h3>Design</h3>
 * <p>The layout version is stored in a single {@code layout.version} file at the data
 * root. It records which on-disk layout scheme the data uses so that the
 * {@link LayoutMigrator} can decide whether an idempotent, versioned relocation of a
 * legacy flat layout into the per-user (multi-user) layout is required.</p>
 *
 * <ul>
 *   <li>{@link #LEGACY_FLAT} ({@code 0}) — the pre-multi-user flat layout
 *       ({@code runtime/} + {@code partitions/} directly under the data root). This is
 *       also the value reported when no {@code layout.version} file is present, so that
 *       deployments predating this feature are treated as legacy flat.</li>
 *   <li>{@link #CURRENT} ({@code 4}) — the multi-user per-user layout, where each user's
 *       data lives under {@code namespaces/AA/BB/{userId}/}.</li>
 * </ul>
 *
 * <p>This class is a stateless utility holding no native resources; it is not
 * {@link AutoCloseable}. All methods are pure with respect to in-memory state; only
 * {@link #write(Path, int)} mutates the filesystem.</p>
 *
 * @see LayoutMigrator
 * @see StorageLayout
 */
public final class DataLayoutVersion {

    private static final Logger log = LoggerFactory.getLogger(DataLayoutVersion.class);

    private DataLayoutVersion() {}

    /** The current data-layout version — the multi-user per-user layout. */
    public static final int CURRENT = 4;

    /**
     * The legacy flat layout version, also reported when no version file exists.
     * Data at this version is a candidate for relocation into the per-user layout.
     */
    public static final int LEGACY_FLAT = 0;

    /** Name of the version marker file stored at the data root. */
    public static final String FILE_LAYOUT_VERSION = "layout.version";

    /**
     * Reads the persisted layout version at {@code dataRoot}.
     *
     * <p>Returns {@link #LEGACY_FLAT} ({@code 0}) when the {@code layout.version} file is
     * absent, empty, or cannot be parsed as an integer, so that untracked or corrupt data
     * is safely treated as legacy flat (and therefore eligible for migration).</p>
     *
     * @param dataRoot the memory persistence root directory
     * @return the stored layout version, or {@link #LEGACY_FLAT} when absent/unreadable
     * @throws NullPointerException if {@code dataRoot} is {@code null}
     * @throws UncheckedIOException if the version file exists but cannot be read
     */
    public static int read(Path dataRoot) {
        Path versionFile = versionFile(dataRoot);
        if (!Files.exists(versionFile)) {
            return LEGACY_FLAT;
        }
        String raw;
        try {
            raw = Files.readString(versionFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read layout version file: " + versionFile, e);
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            log.warn("Empty layout version file at {}; treating as legacy flat (version {})",
                    versionFile, LEGACY_FLAT);
            return LEGACY_FLAT;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            log.warn("Malformed layout version file at {}; treating as legacy flat (version {})",
                    versionFile, LEGACY_FLAT);
            return LEGACY_FLAT;
        }
    }

    /**
     * Writes the layout version {@code v} to the {@code layout.version} file at
     * {@code dataRoot}, creating the data root directory if it does not yet exist.
     *
     * @param dataRoot the memory persistence root directory
     * @param v        the layout version to persist
     * @throws NullPointerException if {@code dataRoot} is {@code null}
     * @throws UncheckedIOException if the version file cannot be written
     */
    public static void write(Path dataRoot, int v) {
        Path versionFile = versionFile(dataRoot);
        try {
            Files.createDirectories(dataRoot);
            Files.writeString(versionFile, Integer.toString(v), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to write layout version file: " + versionFile, e);
        }
    }

    /**
     * Indicates whether the data at {@code dataRoot} uses a legacy flat layout that has
     * not yet been migrated to the current multi-user per-user layout.
     *
     * @param dataRoot the memory persistence root directory
     * @return {@code true} when {@link #read(Path)} reports a version below {@link #CURRENT}
     * @throws NullPointerException if {@code dataRoot} is {@code null}
     * @throws UncheckedIOException if the version file exists but cannot be read
     */
    public static boolean isLegacyFlat(Path dataRoot) {
        return read(dataRoot) < CURRENT;
    }

    private static Path versionFile(Path dataRoot) {
        return dataRoot.resolve(FILE_LAYOUT_VERSION);
    }
}
