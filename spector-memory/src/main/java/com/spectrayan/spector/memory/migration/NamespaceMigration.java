/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.spectrayan.spector.memory.migration;

import java.io.IOException;
import java.nio.file.Path;

/**
 * SPI for namespace schema migrations.
 *
 * <p>Each implementation represents a single migration step that transforms
 * a namespace directory from one schema version to the next. The
 * {@link MigrationPipeline} chains these steps to migrate namespaces
 * across multiple versions.</p>
 *
 * <p>Implementations must be:</p>
 * <ul>
 *   <li><b>Idempotent</b> — running twice on the same namespace must be safe</li>
 *   <li><b>Non-destructive</b> — original data must be recoverable on failure</li>
 *   <li><b>Fast</b> — migrations run on the hot path (namespace load)</li>
 * </ul>
 */
public interface NamespaceMigration {

    /**
     * The source version this migration upgrades from.
     */
    SchemaVersion fromVersion();

    /**
     * The target version this migration upgrades to.
     */
    SchemaVersion toVersion();

    /**
     * A human-readable description of what this migration does.
     */
    String description();

    /**
     * Executes the migration on a namespace directory.
     *
     * <p>The implementation should modify files in-place within the
     * namespace directory. If any step fails, the namespace should
     * remain in a usable state (either old or new format).</p>
     *
     * @param namespaceDir the namespace directory to migrate
     * @throws IOException on migration failure
     */
    void migrate(Path namespaceDir) throws IOException;
}
