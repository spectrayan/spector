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
package com.spectrayan.spector.synapse.dr;

import java.time.Instant;
import java.util.List;

/**
 * Structured compliance audit record for tenant and namespace erasure operations
 * (ADR-0034 §16, Req R6.4, R6.6, R7.5, V7).
 *
 * <p>Invariant V7: Erasure claims only what it can prove. The report explicitly documents
 * the incomplete coverage boundary resulting from the absence of {@code NamespaceRecord.tenantId}.</p>
 */
public record ErasureAuditReport(
        String accountId,
        String namespaceId,
        String operator,
        Instant erasedAt,
        int localFilesDeleted,
        long localBytesDeleted,
        int objectStoreKeysDeleted,
        boolean replicaPropagationDispatched,
        String erasureMechanism,
        boolean cryptoErasePerformed,
        String incompletenessDisclosure,
        List<String> uninspectedClasses
) {
    public static final String MECHANISM_PHYSICAL_TREE_AND_PREFIX = "PHYSICAL_FILESYSTEM_AND_OBJECT_PREFIX_ERASURE";

    public static final String INCOMPLETENESS_DISCLOSURE_TEXT =
            "Incompleteness Disclosure (Req R6.4, V7): Walked account-registered namespace trees only. "
            + "Ownerless namespaces and untenanted-account namespaces were NOT inspected or erased because "
            + "NamespaceRecord does not currently carry tenantId. Completeness cannot be claimed until "
            + "the tenantId schema migration is completed.";

    public static final List<String> UNINSPECTED_CLASSES = List.of(
            "ownerless_namespaces",
            "untenanted_account_namespaces",
            "immutable_system_catalogs"
    );

    public static ErasureAuditReport create(
            String accountId,
            String namespaceId,
            String operator,
            int localFilesDeleted,
            long localBytesDeleted,
            int objectStoreKeysDeleted,
            boolean replicaPropagationDispatched
    ) {
        return new ErasureAuditReport(
                accountId,
                namespaceId,
                operator != null ? operator : "system",
                Instant.now(),
                localFilesDeleted,
                localBytesDeleted,
                objectStoreKeysDeleted,
                replicaPropagationDispatched,
                MECHANISM_PHYSICAL_TREE_AND_PREFIX,
                false, // Task 5.12: No crypto-erase claims while no DEK exists!
                INCOMPLETENESS_DISCLOSURE_TEXT,
                UNINSPECTED_CLASSES
        );
    }
}
