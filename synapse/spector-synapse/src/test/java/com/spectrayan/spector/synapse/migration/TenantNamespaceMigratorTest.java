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
package com.spectrayan.spector.synapse.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.catalog.file.FileAccountCatalog;
import com.spectrayan.spector.synapse.migration.TenantNamespaceMigrator.MigrationSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration test suite verifying Requirements R5.1–R5.8, Tasks 4.1–4.3, and Task 4.7.
 */
@DisplayName("Task 4.7: TenantNamespaceMigrator full test suite")
class TenantNamespaceMigratorTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private FileAccountCatalog catalog;

    private static final String TENANT_ACME = "018f9b8c000070008000000000000001";
    private static final String ALICE_ID = "018f9b8c000070008000000000000002";
    private static final String BOB_ID = "018f9b8c000070008000000000000003";
    private static final String CHARLIE_UNTENANTED = "018f9b8c000070008000000000000004";

    private static final String NS_ALICE = "018f9b8c000070008000000000000010";
    private static final String NS_BOB = "018f9b8c000070008000000000000020";
    private static final String NS_CHARLIE = "018f9b8c000070008000000000000030";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        meterRegistry = new SimpleMeterRegistry();
        catalog = new FileAccountCatalog(tempDir, objectMapper, true);
    }

    private void seedAccount(String accountId, String tenantId, String namespaceId) {
        Account account = new Account(accountId, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Default Account", AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                namespaceId, Instant.now(), tenantId, false);
        Path accountDir = StoragePaths.accountDir(tempDir, accountId);
        try {
            Files.createDirectories(accountDir);
            objectMapper.writeValue(accountDir.resolve("account.json").toFile(), account);

            NamespaceRecord record = new NamespaceRecord(
                    namespaceId, "default", accountId,
                    NamespaceType.DEFAULT, NamespaceStatus.ACTIVE,
                    "Default", "Test namespace", null, Instant.now(), Instant.now()
            );
            objectMapper.writeValue(accountDir.resolve("namespaces.json").toFile(), Map.of(namespaceId, record));
            objectMapper.writeValue(accountDir.resolve("slugs.json").toFile(), Map.of("default", namespaceId));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String seedNamespaceAtLayoutA(String namespaceId, byte[] payload) throws IOException {
        Path nsDir = NamespacePathResolver.resolve(tempDir, null, namespaceId).dir();
        Files.createDirectories(nsDir);
        Files.writeString(nsDir.resolve(StoragePaths.FILE_NAMESPACE), """
                {
                  "layout": "StoragePaths.namespaceDirSharded",
                  "namespaceId": "%s",
                  "createdAt": "2026-01-01T00:00:00Z"
                }
                """.formatted(namespaceId));

        Path dataFile = nsDir.resolve("records.dat");
        Files.write(dataFile, payload);
        return sha256(payload);
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("R5 acceptance: Seed 3 namespaces (2 tenanted, 1 not); dry-run; live migration; SHA-256 check; idempotency")
    void fullMigrationLifecycle_twoTenantedOneUntenanted() throws IOException {
        // Seed accounts in catalog
        seedAccount(ALICE_ID, TENANT_ACME, NS_ALICE);
        seedAccount(BOB_ID, TENANT_ACME, NS_BOB);
        seedAccount(CHARLIE_UNTENANTED, null, NS_CHARLIE);

        byte[] alicePayload = "Alice data payload with sensitive content 12345".getBytes(StandardCharsets.UTF_8);
        byte[] bobPayload = "Bob knowledge graph nodes and embeddings 67890".getBytes(StandardCharsets.UTF_8);
        byte[] charliePayload = "Charlie untracked solo developer memory data".getBytes(StandardCharsets.UTF_8);

        String aliceHash = seedNamespaceAtLayoutA(NS_ALICE, alicePayload);
        String bobHash = seedNamespaceAtLayoutA(NS_BOB, bobPayload);
        String charlieHash = seedNamespaceAtLayoutA(NS_CHARLIE, charliePayload);

        Path aliceLayoutA = NamespacePathResolver.resolve(tempDir, null, NS_ALICE).dir();
        Path bobLayoutA = NamespacePathResolver.resolve(tempDir, null, NS_BOB).dir();
        Path charlieLayoutA = NamespacePathResolver.resolve(tempDir, null, NS_CHARLIE).dir();

        Path aliceLayoutB = NamespacePathResolver.resolve(tempDir, TENANT_ACME, NS_ALICE).dir();
        Path bobLayoutB = NamespacePathResolver.resolve(tempDir, TENANT_ACME, NS_BOB).dir();

        // ── Phase 1: Dry-run ──
        MigrationSummary drySummary = TenantNamespaceMigrator.migrate(
                tempDir, catalog, null, meterRegistry, objectMapper, true);
        assertThat(drySummary.migrated()).isEqualTo(2);
        assertThat(drySummary.skipped()).isEqualTo(0);
        assertThat(drySummary.errors()).isEqualTo(0);

        // Dry run must NOT modify filesystem
        assertThat(Files.exists(aliceLayoutA)).isTrue();
        assertThat(Files.exists(bobLayoutA)).isTrue();
        assertThat(Files.exists(charlieLayoutA)).isTrue();
        assertThat(Files.exists(aliceLayoutB)).isFalse();
        assertThat(Files.exists(bobLayoutB)).isFalse();

        // ── Phase 2: Live Migration ──
        MigrationSummary liveSummary = TenantNamespaceMigrator.migrate(
                tempDir, catalog, null, meterRegistry, objectMapper, false);
        assertThat(liveSummary.migrated()).isEqualTo(2);
        assertThat(liveSummary.skipped()).isEqualTo(0);
        assertThat(liveSummary.errors()).isEqualTo(0);

        // Tenanted namespaces relocated to layout B
        assertThat(Files.exists(aliceLayoutA)).isFalse();
        assertThat(Files.exists(bobLayoutA)).isFalse();
        assertThat(Files.exists(aliceLayoutB)).isTrue();
        assertThat(Files.exists(bobLayoutB)).isTrue();

        // Untenanted namespace remains untouched at layout A (R5.1)
        assertThat(Files.exists(charlieLayoutA)).isTrue();

        // Byte-for-byte SHA-256 equality of data files (R5 acceptance)
        byte[] migratedAliceData = Files.readAllBytes(aliceLayoutB.resolve("records.dat"));
        byte[] migratedBobData = Files.readAllBytes(bobLayoutB.resolve("records.dat"));
        byte[] intactCharlieData = Files.readAllBytes(charlieLayoutA.resolve("records.dat"));

        assertThat(sha256(migratedAliceData)).isEqualTo(aliceHash);
        assertThat(sha256(migratedBobData)).isEqualTo(bobHash);
        assertThat(sha256(intactCharlieData)).isEqualTo(charlieHash);

        // Layout marker in relocated namespaces records the resolver identity, matching what
        // NamespaceResolver and FileAccountCatalog write. Asserting the enum name here previously
        // locked in a value no other writer produced (Req R8.1, R8.3).
        String tenantLayoutId = NamespacePathResolver.Layout.TENANT_SHA256.id();
        JsonNode aliceMarker = objectMapper.readTree(aliceLayoutB.resolve(StoragePaths.FILE_NAMESPACE).toFile());
        assertThat(aliceMarker.get("layout").asText()).isEqualTo(tenantLayoutId);
        assertThat(aliceMarker.get("pathHelper").asText())
                .as("pathHelper must name the live resolver, never the deprecated tenantNamespaceDirSharded")
                .isEqualTo(tenantLayoutId);
        assertThat(aliceMarker.get("tenantId").asText()).isEqualTo(TENANT_ACME);
        assertThat(aliceMarker.get("namespaceId").asText()).isEqualTo(NS_ALICE);

        // ── Phase 3: Idempotent second run ──
        MigrationSummary secondSummary = TenantNamespaceMigrator.migrate(
                tempDir, catalog, null, meterRegistry, objectMapper, false);
        assertThat(secondSummary.migrated()).isEqualTo(0);
        assertThat(secondSummary.skipped()).isEqualTo(2); // Alice & Bob skipped (layout A no longer exists)
        assertThat(secondSummary.errors()).isEqualTo(0);

        // Micrometer counters check (Req R12.2)
        assertThat(meterRegistry.counter("spector.namespace.migration.migrated").count()).isEqualTo(2.0);
        assertThat(meterRegistry.counter("spector.namespace.migration.skipped").count()).isEqualTo(2.0);
        assertThat(meterRegistry.counter("spector.namespace.migration.errors").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Task 4.2 / Req R5.4: Lease guard refuses migration of active/leased namespace without aborting others")
    void leaseGuard_refusesActiveNamespace() throws IOException {
        seedAccount(ALICE_ID, TENANT_ACME, NS_ALICE);
        seedAccount(BOB_ID, TENANT_ACME, NS_BOB);

        seedNamespaceAtLayoutA(NS_ALICE, "Alice data".getBytes(StandardCharsets.UTF_8));
        seedNamespaceAtLayoutA(NS_BOB, "Bob data".getBytes(StandardCharsets.UTF_8));

        Path aliceLayoutA = NamespacePathResolver.resolve(tempDir, null, NS_ALICE).dir();
        Path bobLayoutA = NamespacePathResolver.resolve(tempDir, null, NS_BOB).dir();
        Path bobLayoutB = NamespacePathResolver.resolve(tempDir, TENANT_ACME, NS_BOB).dir();

        // Alice is leased/active
        MigrationSummary summary = TenantNamespaceMigrator.migrate(
                tempDir, catalog, nsId -> nsId.equals(NS_ALICE), meterRegistry, objectMapper, false);

        assertThat(summary.migrated()).isEqualTo(1); // Bob migrated
        assertThat(summary.errors()).isEqualTo(1);   // Alice refused due to lease
        assertThat(summary.hasErrors()).isTrue();

        // Alice remained at layout A (untouched)
        assertThat(Files.exists(aliceLayoutA)).isTrue();
        // Bob migrated to layout B
        assertThat(Files.exists(bobLayoutA)).isFalse();
        assertThat(Files.exists(bobLayoutB)).isTrue();
    }

    @Test
    @DisplayName("Task 4.3 / Req R5.5: Staging cleanup cleans leftover .migrating-* directories and crash injection preserves integrity")
    void crashInjectionAndStagingCleanup_leavesNamespaceIntactAtOnePath() throws IOException {
        seedAccount(ALICE_ID, TENANT_ACME, NS_ALICE);
        seedNamespaceAtLayoutA(NS_ALICE, "Alice data".getBytes(StandardCharsets.UTF_8));

        Path aliceLayoutA = NamespacePathResolver.resolve(tempDir, null, NS_ALICE).dir();
        Path aliceLayoutB = NamespacePathResolver.resolve(tempDir, TENANT_ACME, NS_ALICE).dir();

        // Simulate crash leftover: orphaned staging directory in target parent
        Path staging = aliceLayoutB.getParent().resolve(".migrating-" + NS_ALICE);
        Files.createDirectories(staging);
        Files.writeString(staging.resolve("corrupted.tmp"), "partial write");

        // Clean up before or during migration
        int cleaned = TenantNamespaceMigrator.cleanupStagedDirectories(tempDir);
        assertThat(cleaned).isEqualTo(1);
        assertThat(Files.exists(staging)).isFalse();

        // Source at Layout A is completely intact
        assertThat(Files.exists(aliceLayoutA)).isTrue();

        // Run migration now succeeds cleanly
        MigrationSummary summary = TenantNamespaceMigrator.migrate(
                tempDir, catalog, null, meterRegistry, objectMapper, false);
        assertThat(summary.migrated()).isEqualTo(1);
        assertThat(summary.errors()).isEqualTo(0);
        assertThat(Files.exists(aliceLayoutB)).isTrue();
    }

    @Test
    @DisplayName("H1 / Req R5.4: A rememberer root held by another process blocks migration entirely")
    void rootHeldByAnotherProcess_refusesToMigrate() throws Exception {
        seedAccount(ALICE_ID, TENANT_ACME, NS_ALICE);
        byte[] payload = "Alice data".getBytes(StandardCharsets.UTF_8);
        String expectedDigest = seedNamespaceAtLayoutA(NS_ALICE, payload);

        Path layoutA = NamespacePathResolver.resolve(tempDir, null, NS_ALICE).dir();
        Path layoutB = NamespacePathResolver.resolve(tempDir, TENANT_ACME, NS_ALICE).dir();

        // Simulate a running server in a different JVM by taking the lock from a separate channel.
        // A FileLock is JVM-scoped, so an in-JVM tryLock would report HELD_BY_THIS_JVM; locking from
        // a forked process is what genuinely reproduces the contended case.
        Path lockFile = tempDir.resolve(StoragePaths.FILE_LOCK);
        Files.createDirectories(tempDir);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process holder = new ProcessBuilder(java, writeLockHolderProgram(lockFile).toString())
                .redirectErrorStream(true)
                .start();
        try {
            // Wait until the child reports the lock is held.
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            assertThat(line).as("lock holder child process must report readiness").isEqualTo("LOCKED");

            MigrationSummary summary = TenantNamespaceMigrator.migrate(
                    tempDir, catalog, null, meterRegistry, objectMapper, false);

            assertThat(summary.errors()).as("a contended root must be an error, not a silent skip").isEqualTo(1);
            assertThat(summary.migrated()).isZero();
            assertThat(summary.hasErrors()).isTrue();

            // Nothing moved; the source is untouched.
            assertThat(Files.exists(layoutB)).isFalse();
            assertThat(sha256(Files.readAllBytes(layoutA.resolve("records.dat")))).isEqualTo(expectedDigest);
        } finally {
            holder.destroy();
            holder.waitFor();
        }
    }

    /** Writes a single-file Java program that locks {@code lockFile} and blocks until killed. */
    private Path writeLockHolderProgram(Path lockFile) throws IOException {
        Path src = tempDir.resolve("LockHolder.java");
        Files.writeString(src, """
                import java.nio.channels.FileChannel;
                import java.nio.file.Path;
                import java.nio.file.StandardOpenOption;

                public class LockHolder {
                    public static void main(String[] args) throws Exception {
                        try (FileChannel ch = FileChannel.open(Path.of("%s"),
                                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                            ch.lock();
                            System.out.println("LOCKED");
                            System.out.flush();
                            Thread.sleep(600_000);
                        }
                    }
                }
                """.formatted(lockFile.toString().replace("\\", "\\\\")));
        return src;
    }

    @Test
    @DisplayName("H1 / Req R5.4: An in-process guard refusal is an error, and the namespace is left alone")
    void inProcessLeaseGuard_refusesAndPreservesSource() throws IOException {
        seedAccount(ALICE_ID, TENANT_ACME, NS_ALICE);
        byte[] payload = "Alice data".getBytes(StandardCharsets.UTF_8);
        String expectedDigest = seedNamespaceAtLayoutA(NS_ALICE, payload);

        Path layoutA = NamespacePathResolver.resolve(tempDir, null, NS_ALICE).dir();
        Path layoutB = NamespacePathResolver.resolve(tempDir, TENANT_ACME, NS_ALICE).dir();

        MigrationSummary summary = TenantNamespaceMigrator.migrate(
                tempDir, catalog, nsId -> nsId.equals(NS_ALICE), meterRegistry, objectMapper, false);

        assertThat(summary.errors()).isEqualTo(1);
        assertThat(summary.migrated()).isZero();
        assertThat(Files.exists(layoutB)).isFalse();
        assertThat(sha256(Files.readAllBytes(layoutA.resolve("records.dat")))).isEqualTo(expectedDigest);
    }

    @Test
    @DisplayName("C4 / Req R5.5: Discarding a staging copy never loses data, because the source outlives it")
    void stagingCleanup_neverDestroysTheOnlyCopy() throws IOException {
        seedAccount(ALICE_ID, TENANT_ACME, NS_ALICE);
        byte[] payload = "Alice irreplaceable data".getBytes(StandardCharsets.UTF_8);
        String expectedDigest = seedNamespaceAtLayoutA(NS_ALICE, payload);

        Path layoutA = NamespacePathResolver.resolve(tempDir, null, NS_ALICE).dir();
        Path layoutB = NamespacePathResolver.resolve(tempDir, TENANT_ACME, NS_ALICE).dir();

        // Reproduce the exact interrupted state: a staging directory holding a full copy of the
        // namespace, as it exists between the copy and the final rename on a cross-filesystem move.
        Path staging = layoutB.getParent().resolve(".migrating-" + NS_ALICE);
        Files.createDirectories(staging);
        Files.copy(layoutA.resolve("records.dat"), staging.resolve("records.dat"));
        Files.copy(layoutA.resolve(StoragePaths.FILE_NAMESPACE), staging.resolve(StoragePaths.FILE_NAMESPACE));

        // Boot-time cleanup discards the staging copy. This is only safe because the source is still
        // there: the earlier implementation moved the source into staging, so this deletion removed
        // the sole surviving copy of the namespace.
        TenantNamespaceMigrator.cleanupStagedDirectories(tempDir);

        assertThat(Files.exists(staging)).isFalse();
        assertThat(layoutA.resolve("records.dat")).exists();
        assertThat(sha256(Files.readAllBytes(layoutA.resolve("records.dat"))))
                .as("the source payload must be byte-identical after staging cleanup")
                .isEqualTo(expectedDigest);

        // And the namespace is still fully migratable afterwards.
        MigrationSummary summary = TenantNamespaceMigrator.migrate(
                tempDir, catalog, null, meterRegistry, objectMapper, false);
        assertThat(summary.migrated()).isEqualTo(1);
        assertThat(summary.errors()).isZero();
        assertThat(sha256(Files.readAllBytes(layoutB.resolve("records.dat")))).isEqualTo(expectedDigest);
    }

    @Test
    @DisplayName("C4 / Req R5.2: Target populated plus surviving source repairs the marker and keeps both")
    void interruptedAfterTargetRename_repairsMarkerAndPreservesBothCopies() throws IOException {
        seedAccount(ALICE_ID, TENANT_ACME, NS_ALICE);
        byte[] payload = "Alice data".getBytes(StandardCharsets.UTF_8);
        String expectedDigest = seedNamespaceAtLayoutA(NS_ALICE, payload);

        Path layoutA = NamespacePathResolver.resolve(tempDir, null, NS_ALICE).dir();
        Path layoutB = NamespacePathResolver.resolve(tempDir, TENANT_ACME, NS_ALICE).dir();

        // Reproduce a crash after the target rename but before the source was reclaimed: the target
        // is populated, the source survives, and the target's marker still says layout A.
        Files.createDirectories(layoutB);
        Files.copy(layoutA.resolve("records.dat"), layoutB.resolve("records.dat"));
        Files.copy(layoutA.resolve(StoragePaths.FILE_NAMESPACE), layoutB.resolve(StoragePaths.FILE_NAMESPACE));

        MigrationSummary summary = TenantNamespaceMigrator.migrate(
                tempDir, catalog, null, meterRegistry, objectMapper, false);

        // Never merged, never overwritten, never auto-deleted.
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.migrated()).isZero();
        assertThat(summary.errors()).isZero();
        assertThat(Files.exists(layoutA)).as("a stale source must be reported, not silently deleted").isTrue();
        assertThat(sha256(Files.readAllBytes(layoutB.resolve("records.dat")))).isEqualTo(expectedDigest);

        // The half-finished marker is repaired so the resolver's layout check does not reject the
        // namespace on open (Req R8.2).
        JsonNode marker = objectMapper.readTree(layoutB.resolve(StoragePaths.FILE_NAMESPACE).toFile());
        assertThat(marker.get("layout").asText())
                .isEqualTo(NamespacePathResolver.Layout.TENANT_SHA256.id());
        assertThat(marker.get("tenantId").asText()).isEqualTo(TENANT_ACME);
    }
}
