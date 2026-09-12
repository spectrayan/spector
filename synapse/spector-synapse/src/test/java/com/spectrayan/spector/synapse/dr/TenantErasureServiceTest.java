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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceLegalHoldException;
import com.spectrayan.spector.synapse.catalog.file.FileAccountCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Specifications and unit/integration tests for TenantErasureService
 * (ADR-0034 §16, Phase 6, Req R6.1–R6.8, R7.5, V7, V8).
 */
@DisplayName("Phase 6 Group 5: Physical Erasure & Residency Compliance Specification")
class TenantErasureServiceTest {

    private static final String BUCKET = "spector-dr-erasure-bucket";

    @TempDir
    Path tempDir;

    private EmbeddedS3Server s3Server;
    private S3CompatibleObjectStoreClient s3Client;
    private FileAccountCatalog catalog;
    private Path remembererDir;
    private List<String> dispatchedReplicas;
    private TenantErasureService erasureService;

    @BeforeEach
    void setUp() throws Exception {
        s3Server = new EmbeddedS3Server();
        s3Server.createBucket(BUCKET);
        s3Client = new S3CompatibleObjectStoreClient(s3Server.getEndpoint(), "us-east-1", "key", "sec", 0L);

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        catalog = new FileAccountCatalog(tempDir.resolve("catalog"), mapper);

        remembererDir = tempDir.resolve("data");
        Files.createDirectories(remembererDir);

        dispatchedReplicas = new ArrayList<>();
        erasureService = new TenantErasureService(
                catalog,
                s3Client,
                BUCKET,
                remembererDir,
                dispatchedReplicas::add
        );
    }

    @AfterEach
    void tearDown() {
        if (s3Server != null) {
            s3Server.close();
        }
    }

    @Test
    @DisplayName("R6.2, R6.3: Physical erasure deletes local files, cloud prefixes, and dispatches to replicas")
    void testPhysicalErasureComplete() throws IOException {
        String accountId = "acc-erase-01";
        catalog.getOrCreateAccount(accountId);
        NamespaceRecord ns = catalog.createNamespace(accountId, "finance-vault", NamespaceType.PROJECT);

        // 1. Create local files on disk
        Path nsDiskDir = remembererDir.resolve("namespaces").resolve(ns.namespaceId());
        Files.createDirectories(nsDiskDir);
        Files.writeString(nsDiskDir.resolve("partition-1.spct"), "partition-content-bytes");
        Files.writeString(nsDiskDir.resolve("index.spct"), "index-bytes");

        // 2. Create S3 cloud backup keys
        String prefix = "snapshots/" + accountId + "/" + ns.namespaceId() + "/";
        s3Client.putObject(BUCKET, prefix + "manifest.json", "manifest".getBytes(StandardCharsets.UTF_8), null, null);
        s3Client.putObject(BUCKET, prefix + "partition-1.spct", "partition".getBytes(StandardCharsets.UTF_8), null, null);

        // 3. Execute erasure
        ErasureAuditReport report = erasureService.eraseNamespace(accountId, "finance-vault", true, "compliance-auditor");

        // Assert local disk is wiped
        assertThat(Files.exists(nsDiskDir)).isFalse();
        assertThat(report.localFilesDeleted()).isGreaterThanOrEqualTo(2);
        assertThat(report.localBytesDeleted()).isGreaterThan(0L);

        // Assert S3 DR bucket prefix is deleted
        assertThat(s3Client.listKeysByPrefix(BUCKET, prefix)).isEmpty();
        assertThat(report.objectStoreKeysDeleted()).isGreaterThanOrEqualTo(2);

        // Assert replica dispatch
        assertThat(dispatchedReplicas).contains(ns.namespaceId());
        assertThat(report.replicaPropagationDispatched()).isTrue();

        // Assert mechanism and no false crypto-erase claims (Task 5.12, Req R7.5)
        assertThat(report.erasureMechanism()).isEqualTo(ErasureAuditReport.MECHANISM_PHYSICAL_TREE_AND_PREFIX);
        assertThat(report.cryptoErasePerformed()).isFalse();
    }

    @Test
    @DisplayName("R6.7, V8: Legal hold strictly BLOCKS erasure via NamespaceLegalHoldException")
    void testLegalHoldBlocksErasure() {
        String accountId = "acc-legal-held";
        catalog.getOrCreateAccount(accountId);
        NamespaceRecord ns = catalog.createNamespace(accountId, "subpoena-data", NamespaceType.PROJECT);
        catalog.setLegalHold(accountId, "subpoena-data", true);

        assertThatThrownBy(() -> erasureService.eraseNamespace(accountId, "subpoena-data", true, "admin"))
                .isInstanceOf(NamespaceLegalHoldException.class)
                .hasMessageContaining(ns.namespaceId());
    }

    @Test
    @DisplayName("R6.4, R10.4, V7: Erase audit report explicitly discloses incomplete inspection boundaries")
    void testIncompletenessDisclosureInReport() {
        String accountId = "acc-disclose";
        catalog.getOrCreateAccount(accountId);
        catalog.createNamespace(accountId, "test-disclose", NamespaceType.PROJECT);

        ErasureAuditReport report = erasureService.eraseNamespace(accountId, "test-disclose", false, "auditor");

        assertThat(report.incompletenessDisclosure())
                .as("Report must disclose incomplete inspection due to lack of NamespaceRecord.tenantId (Req R6.4, V7)")
                .contains("NamespaceRecord does not currently carry tenantId")
                .contains("Incompleteness Disclosure");

        assertThat(report.uninspectedClasses())
                .as("Report must name the classes of data not inspected")
                .contains("ownerless_namespaces", "untenanted_account_namespaces");
    }
}
