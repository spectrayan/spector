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
package com.spectrayan.spector.synapse.config;

import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.memory.LayoutMigrator;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import com.spectrayan.spector.synapse.security.UserAccountStore;

/**
 * Wires the one-time, startup-side multi-user bootstrapping into the application lifecycle: the
 * versioned data-layout migration and the idempotent default-admin seeding (Requirements 1.5,
 * 12.7, 17.1).
 *
 * <h3>Behavior</h3>
 * <ul>
 *   <li>When {@code spector.auth.enabled=true}: on {@link ApplicationReadyEvent} this component runs
 *       {@link LayoutMigrator#migrateIfNeeded(Path, String)} to relocate any legacy flat layout into
 *       the default user's per-user namespace, then calls
 *       {@link UserAccountStore#seedDefaultAdmin(String)} so a fresh deployment always has a login.
 *       Both operations are idempotent, so repeated startups converge without duplicate work.</li>
 *   <li>When {@code spector.auth.enabled=false}: neither the migration nor the seeding runs, so the
 *       existing flat filesystem layout is left untouched and no per-user sharded directories or
 *       accounts are created — byte-for-byte the legacy single-user behavior (Requirement 1.5).</li>
 * </ul>
 *
 * <h3>Ordering</h3>
 * <p>The work is bound to {@link ApplicationReadyEvent}, which fires only after the application
 * context is fully refreshed. Flyway (invoked during context refresh) has therefore already created
 * the {@code users} table before {@link UserAccountStore#seedDefaultAdmin(String)} runs.</p>
 *
 * <h3>Default user id</h3>
 * <p>The migration relocates the flat layout into the <em>anonymous / default</em> principal's
 * namespace. Per the design (see "Anonymous fallback" and "Data-layout versioning and migration"),
 * the anonymous principal resolves to the literal user id {@value #DEFAULT_USER_ID}, and per-user
 * memory routing returns the shared/flat instance for exactly that id. Relocating the flat layout
 * into the {@value #DEFAULT_USER_ID} namespace therefore keeps the pre-existing single-user data
 * reachable and is the consistent choice — it is deliberately <strong>not</strong> the seeded
 * admin's TSID (which owns no pre-existing data).</p>
 *
 * <h3>Configuration source</h3>
 * <p>The default admin password comes from {@code spector.auth.default-admin.password} (sourced from
 * {@code ${env:SPECTOR_ADMIN_PASSWORD}}); {@code AuthPropertiesValidator} already guarantees it is
 * non-empty whenever auth is enabled. Secrets are never logged.</p>
 */
@Component
public class AuthStartupInitializer {

    private static final Logger log = LoggerFactory.getLogger(AuthStartupInitializer.class);

    /**
     * Literal user id of the anonymous / default principal. The legacy flat layout is migrated into
     * this namespace so the pre-existing single-user data stays reachable once auth is enabled.
     */
    private static final String DEFAULT_USER_ID = "default";

    private final SynapseProperties synapseProps;
    private final SpectorConfigProperties spectorProps;
    private final UserAccountStore accountStore;

    /**
     * @param synapseProps bound {@code spector.*} configuration (auth toggle, {@code dataDir},
     *                     default-admin password)
     * @param spectorProps embedded Spector config (memory persistence path)
     * @param accountStore JDBC-backed account store providing idempotent default-admin seeding
     */
    public AuthStartupInitializer(
            SynapseProperties synapseProps,
            SpectorConfigProperties spectorProps,
            UserAccountStore accountStore) {
        this.synapseProps = Objects.requireNonNull(synapseProps, "synapseProps");
        this.spectorProps = Objects.requireNonNull(spectorProps, "spectorProps");
        this.accountStore = Objects.requireNonNull(accountStore, "accountStore");
    }

    /**
     * Runs the layout migration and default-admin seeding once the context (and thus Flyway) is
     * fully initialized. A no-op when {@code spector.auth.enabled=false}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeAuth() {
        if (!synapseProps.auth().enabled()) {
            log.debug("[AuthStartup] auth disabled; skipping layout migration and default-admin seeding");
            return;
        }

        Path dataRoot = basePath();
        log.info("[AuthStartup] auth enabled; migrating layout at {} into namespace of user '{}'",
                dataRoot, DEFAULT_USER_ID);
        LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID);

        accountStore.seedDefaultAdmin(synapseProps.auth().defaultAdmin().password());
        log.info("[AuthStartup] auth startup initialization complete");
    }

    /**
     * Resolves the base persistence root: {@code spector.memory.persistence-path} when set,
     * otherwise the Synapse {@code dataDir}. Mirrors {@code UserMemoryRegistry#basePath()} so the
     * migration relocates the same tree the per-user registry roots its instances at.
     */
    private Path basePath() {
        String path = spectorProps.getMemory().getPersistencePath();
        if (path == null || path.isBlank()) {
            path = synapseProps.dataDir();
        }
        return Path.of(path);
    }
}
