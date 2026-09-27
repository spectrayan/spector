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
package com.spectrayan.spector.synapse.cluster.failover;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.fencing.FenceToken;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RouteMode;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.store.JdbcControlStore;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.synapse.cluster.fencing.FencedException;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0-1: Process-level owner kill failover integration test measuring real wall-clock RTO and RPO.
 *
 * <p>Requirements verified:
 * <ul>
 *   <li>100 durable remembers written by a genuine separate JVM owner process using {@link JdbcControlStore}</li>
 *   <li>Unclean process kill ({@code kill -9} via {@link Process#destroyForcibly()})</li>
 *   <li>Survivor advances epoch in JDBC and mints new fencing token</li>
 *   <li>Survivor opens same mmap/WAL bundles from disk and directly counts recovered IDs</li>
 *   <li>First write on survivor succeeds under new epoch</li>
 *   <li>Stale epoch writes from dead owner are fenced</li>
 *   <li>Published RTO (detect &rarr; first accepted write) and RPO (0 lost committed IDs)</li>
 * </ul>
 */
@DisplayName("P0-1: Process-level owner kill-9 failover with JdbcControlStore")
class ProcessKillOwnerRecoveryTest {

    private static final Logger log = LoggerFactory.getLogger(ProcessKillOwnerRecoveryTest.class);

    private static final String NAMESPACE_ID = "ns-proc-kill";
    private static final int WRITE_COUNT = 100;

    @Test
    @DisplayName("kill -9 owner process: verify survivor reopens bundles, counts IDs, achieves RPO=0, and accepts write under new epoch")
    void testProcessLevelOwnerKillRecovery(@TempDir Path tempDir) throws Exception {
        Path h2DbPath = tempDir.resolve("control-store-h2");
        String h2Url = "jdbc:h2:" + h2DbPath.toAbsolutePath() + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
        Path storageDir = tempDir.resolve("storage-root");
        Files.createDirectories(storageDir);

        // ── Step 1: Initialize JdbcControlStore & coordinator in test runner (survivor/coordinator) ──
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(h2Url);
        JdbcControlStore store = new JdbcControlStore(ds, Clock.systemUTC());

        List<String> members = List.of("node-owner", "node-survivor");
        store.updateMembership(new CellMembership("cell-1", 1, members));

        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(
                store, "node-survivor", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        // Pre-mint initial epoch for owner under coordinator authority
        long expectedInitialEpoch = store.advanceNamespaceEpoch(NAMESPACE_ID, coordMgr.getLeaseVersion());
        assertThat(expectedInitialEpoch).isEqualTo(1L);

        // ── Step 2: Fork owner in a separate JVM process ──
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "--enable-preview",
                "--add-modules=jdk.incubator.vector",
                "-cp", classpath,
                OwnerProcessMain.class.getName(),
                h2Url,
                storageDir.toAbsolutePath().toString(),
                NAMESPACE_ID,
                String.valueOf(WRITE_COUNT)
        );
        pb.redirectErrorStream(true);

        log.info("Launching owner child JVM process...");
        Process ownerProcess = pb.start();

        long initialEpoch;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ownerProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String readyLine = null;
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Owner JVM] " + line);
                if (line.startsWith("OWNER_READY:")) {
                    readyLine = line;
                    break;
                }
            }

            assertThat(readyLine)
                    .as("Owner process must write 100 durable memories and report readiness")
                    .isNotNull();

            String[] parts = readyLine.split(":");
            initialEpoch = Long.parseLong(parts[1]);
            int reportedWrites = Integer.parseInt(parts[2]);
            assertThat(reportedWrites).isEqualTo(WRITE_COUNT);
            assertThat(initialEpoch).isEqualTo(expectedInitialEpoch);
        }

        assertThat(ownerProcess.isAlive())
                .as("Owner process must still be running actively when kill signal is sent")
                .isTrue();

        // ── Step 3: KILL -9 OWNER PROCESS (destroyForcibly sends SIGKILL) ──
        long killStartTimeMs = System.currentTimeMillis();
        System.out.println("KILL -9 sent to owner PID " + ownerProcess.pid() + " at t=0ms");

        ownerProcess.destroyForcibly();
        boolean exited = ownerProcess.waitFor(10, TimeUnit.SECONDS);
        assertThat(exited).as("Owner process must terminate promptly after SIGKILL").isTrue();
        // ── Step 4: Advance epoch in JDBC control store for survivor promotion ──
        long newEpoch = store.advanceNamespaceEpoch(NAMESPACE_ID, coordMgr.getLeaseVersion());
        assertThat(newEpoch).isGreaterThan(initialEpoch);
        FenceToken newFenceToken = fenceMgr.mintFenceForEpoch(NAMESPACE_ID, newEpoch);
        String newFence = newFenceToken.toTokenString();

        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 1, members);
        OwnershipResolver survivorResolver = new OwnershipResolver(
                new NodeIdentity("cell-1", "node-survivor", NodeRole.OWNER), membership, overrideMgr);
        OwnershipResolver ownerResolver = new OwnershipResolver(
                new NodeIdentity("cell-1", "node-owner", NodeRole.OWNER), membership, overrideMgr);

        MemoryRequestBinder survivorBinder = new MemoryRequestBinder(null, null, null, survivorResolver, fenceMgr);
        MemoryRequestBinder oldOwnerBinder = new MemoryRequestBinder(null, null, null, ownerResolver, fenceMgr);

        // ── Step 5: Survivor reopens on-disk mmap bundles and counts recovered records ──
        var memProps = new MemoryProperties()
                .setCapacity(WRITE_COUNT * 2)
                .setDimensions(64)
                .setWorkingCapacity(10)
                .setEpisodicPartitionCapacity(WRITE_COUNT * 2)
                .setSemanticCapacity(WRITE_COUNT * 2)
                .setProceduralCapacity(WRITE_COUNT * 2);
        memProps.getRemember().setSurpriseWarmup(1);

        int countFoundOnDisk;
        try (SpectorMemory survivorMemory = DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(new OwnerProcessMain.SimpleTestEmbedder(64))
                .namespaceId(NAMESPACE_ID)
                .persistence(storageDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .build()) {

            // Count recovered records directly from partition bundles / mmap storage (not a test counter!)
            countFoundOnDisk = survivorMemory.totalMemories();
            assertThat(countFoundOnDisk)
                    .as("All 100 durable writes committed by dead owner must be present in survivor mmap storage")
                    .isEqualTo(WRITE_COUNT);

            // Verify WAL also contains all events on disk
            try (com.spectrayan.spector.memory.sync.MemoryWal diskWal = new com.spectrayan.spector.memory.sync.MemoryWal(storageDir.resolve("wal"))) {
                assertThat(diskWal.size())
                        .as("WAL must contain durable event logs for the pre-kill writes")
                        .isGreaterThanOrEqualTo(WRITE_COUNT);
            }

            // ── Step 5: Survivor accepts first write under newly minted epoch ──
            assertThatCode(() -> survivorBinder.enforceFence(NAMESPACE_ID, newFence))
                    .doesNotThrowAnyException();

            survivorMemory.remember(
                    "survivor-mem-101",
                    "Survivor accepted write under new epoch after owner kill -9",
                    MemoryType.SEMANTIC,
                    "failover", "promoted"
            );

            // Verify count incremented to 101
            assertThat(survivorMemory.totalMemories()).isEqualTo(WRITE_COUNT + 1);
        }

        long recoveryTimeMs = System.currentTimeMillis();
        long publishedRtoMs = recoveryTimeMs - killStartTimeMs;
        long publishedRpoLoss = WRITE_COUNT - countFoundOnDisk;

        // ── Step 6: Verify old owner writes are strictly rejected (fenced) ──
        assertThatThrownBy(() -> oldOwnerBinder.enforceFence(NAMESPACE_ID, String.valueOf(initialEpoch)))
                .isInstanceOf(FencedException.class);

        // ── Assertions ──
        assertThat(countFoundOnDisk)
                .as("All 100 durable writes committed by dead owner must be present on survivor")
                .isEqualTo(WRITE_COUNT);
        assertThat(publishedRpoLoss)
                .as("RPO data loss for synchronized WAL state must be exactly 0")
                .isEqualTo(0L);

        log.info("[P0-1 Kill-Owner Recovery Benchmark Report]");
        log.info("  Kill mechanism: OS SIGKILL (Process.destroyForcibly)");
        log.info("  Owner initial epoch: {}", initialEpoch);
        log.info("  Survivor promoted epoch: {}", newEpoch);
        log.info("  Durable records recovered on survivor: {} / {}", countFoundOnDisk, WRITE_COUNT);
        log.info("  Published RPO Data Loss: {} records", publishedRpoLoss);
        log.info("  Published RTO (kill -> first accepted write): {} ms", publishedRtoMs);

        assertThat(publishedRtoMs).isLessThanOrEqualTo(30_000L);
    }
}
