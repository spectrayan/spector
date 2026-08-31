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

    /**
     * Resolves the path to an account's {@code identity.bundle} under a specific tenant hierarchy (ADR-0029 §4.3).
     *
     * @param dataDir   synapse root data directory
     * @param tenantId  the unique tenant identifier
     * @param accountId the account identifier
     * @return absolute path to the tenant-scoped account identity.bundle
     */
    public static Path tenantAccountIdentityBundle(Path dataDir, String tenantId, String accountId) {
        Objects.requireNonNull(dataDir, "dataDir must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");

        String ts1 = shard1(tenantId);
        String ts2 = shard2(tenantId);
        String as1 = shard1(accountId);
        String as2 = shard2(accountId);

        return dataDir.resolve(DIR_IDENTITY)
                .resolve(DIR_TENANTS)
                .resolve(ts1)
                .resolve(ts2)
                .resolve(tenantId)
                .resolve(DIR_ACCOUNTS)
                .resolve(as1)
                .resolve(as2)
                .resolve(accountId)
                .resolve(FILE_IDENTITY_BUNDLE);
    }

    /**
     * Resolves the enterprise tenant-rooted namespace directory (ADR-0029 §4.3).
     *
     * @param dataDir     synapse root data directory
     * @param tenantId    the unique tenant identifier
     * @param namespaceId the unique namespace identifier (TSID)
     * @return absolute path to the enterprise namespace data directory
     */
    public static Path enterpriseNamespaceDir(Path dataDir, String tenantId, String namespaceId) {
        Objects.requireNonNull(dataDir, "dataDir must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");

        String ts1 = shard1(tenantId);
        String ts2 = shard2(tenantId);
        String ns1 = shard1(namespaceId);
        String ns2 = shard2(namespaceId);

        return dataDir.resolve(DIR_TENANTS)
                .resolve(ts1)
                .resolve(ts2)
                .resolve(tenantId)
                .resolve("namespaces")
                .resolve(ns1)
                .resolve(ns2)
                .resolve(namespaceId);
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
