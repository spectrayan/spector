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
package com.spectrayan.spector.synapse.identity;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Calculates sharded on-disk paths for account and tenant identity bundles (ADR-0029 §23.2).
 *
 * <pre>
 * ${SPECTOR_DATA_DIR}/
 *   db/synapse.mv.db
 *   identity/
 *     accounts/{aa}/{bb}/{accountId}/identity.bundle
 *     tenants/{tt}/{uu}/{tenantId}/identity.bundle
 *   cognitive/
 *     namespaces/{xx}/{yy}/{namespaceId}/
 * </pre>
 */
public final class IdentityPaths {

    public static final String DIR_IDENTITY = "identity";
    public static final String DIR_ACCOUNTS = "accounts";
    public static final String DIR_TENANTS = "tenants";
    public static final String FILE_IDENTITY_BUNDLE = "identity.bundle";

    private IdentityPaths() {
    }

    /**
     * Resolves the path to an account's {@code identity.bundle}.
     *
     * @param dataDir   synapse root data directory
     * @param accountId the TSID or unique account identifier
     * @return absolute path to the account's identity.bundle
     */
    public static Path accountIdentityBundle(Path dataDir, String accountId) {
        Objects.requireNonNull(dataDir, "dataDir must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");

        String s1 = shard1(accountId);
        String s2 = shard2(accountId);

        return dataDir.resolve(DIR_IDENTITY)
                .resolve(DIR_ACCOUNTS)
                .resolve(s1)
                .resolve(s2)
                .resolve(accountId)
                .resolve(FILE_IDENTITY_BUNDLE);
    }

    /**
     * Resolves the path to a tenant's {@code identity.bundle}.
     *
     * @param dataDir  synapse root data directory
     * @param tenantId the unique tenant identifier
     * @return absolute path to the tenant's identity.bundle
     */
    public static Path tenantIdentityBundle(Path dataDir, String tenantId) {
        Objects.requireNonNull(dataDir, "dataDir must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        String s1 = shard1(tenantId);
        String s2 = shard2(tenantId);

        return dataDir.resolve(DIR_IDENTITY)
                .resolve(DIR_TENANTS)
                .resolve(s1)
                .resolve(s2)
                .resolve(tenantId)
                .resolve(FILE_IDENTITY_BUNDLE);
    }

    private static String shard1(String id) {
        if (id.length() < 2) {
            return "00";
        }
        return id.substring(0, 2).toLowerCase();
    }

    private static String shard2(String id) {
        if (id.length() < 4) {
            return "00";
        }
        return id.substring(2, 4).toLowerCase();
    }
}
