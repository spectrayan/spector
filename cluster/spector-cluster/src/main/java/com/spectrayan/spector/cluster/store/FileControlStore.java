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
package com.spectrayan.spector.cluster.store;

import com.spectrayan.spector.cluster.membership.CellMembership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Atomic file-backed implementation of {@link ControlStore} for Docker Compose multi-node testing,
 * development clusters, and local deployments without Kubernetes (Req R1.1, R1.7).
 *
 * <p>Persists state using Jackson 3 to a designated JSON file with crash-safe temporary write and
 * atomic file movement. Concurrency is guarded via {@link ReentrantLock}.</p>
 */
public class FileControlStore implements ControlStore {

    private static final Logger log = LoggerFactory.getLogger(FileControlStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path stateFile;
    private final ReentrantLock lock = new ReentrantLock();
    private final Clock clock;

    private StateData data = new StateData();

    public record StateData(
            CellMembership membership,
            CoordinatorLease coordinatorLease,
            Map<String, Long> namespaceEpochs,
            Map<String, OverrideLeaseRecord> overrides) {

        public StateData() {
            this(null, null, new HashMap<>(), new HashMap<>());
        }
    }

    public FileControlStore(Path stateFile) {
        this(stateFile, Clock.systemUTC());
    }

    public FileControlStore(Path stateFile, Clock clock) {
        this.stateFile = Objects.requireNonNull(stateFile, "stateFile must not be null").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        loadOrCreate();
    }

    private void loadOrCreate() {
        lock.lock();
        try {
            if (Files.exists(stateFile)) {
                try (InputStream in = Files.newInputStream(stateFile)) {
                    this.data = MAPPER.readValue(in, StateData.class);
                    if (this.data.namespaceEpochs() == null) {
                        this.data = new StateData(this.data.membership(), this.data.coordinatorLease(),
                                new HashMap<>(), this.data.overrides() != null ? this.data.overrides() : new HashMap<>());
                    }
                    if (this.data.overrides() == null) {
                        this.data = new StateData(this.data.membership(), this.data.coordinatorLease(),
                                this.data.namespaceEpochs(), new HashMap<>());
                    }
                    log.info("[FileControlStore] Loaded cluster control store state from {}", stateFile);
                } catch (IOException e) {
                    log.warn("[FileControlStore] Failed to parse existing state file {}; reinitializing", stateFile, e);
                    this.data = new StateData();
                    persist();
                }
            } else {
                if (stateFile.getParent() != null) {
                    Files.createDirectories(stateFile.getParent());
                }
                this.data = new StateData();
                persist();
                log.info("[FileControlStore] Initialized empty control store state file at {}", stateFile);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize FileControlStore at " + stateFile, e);
        } finally {
            lock.unlock();
        }
    }

    private void persist() {
        try {
            Path parent = stateFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Path tmpFile = stateFile.resolveSibling(stateFile.getFileName().toString() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmpFile)) {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(out, this.data);
            }
            try {
                Files.move(tmpFile, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist control store state to " + stateFile, e);
        }
    }

    @Override
    public CellMembership getMembership() {
        lock.lock();
        try {
            return data.membership();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateMembership(CellMembership membership) {
        Objects.requireNonNull(membership, "membership must not be null");
        lock.lock();
        try {
            this.data = new StateData(membership, data.coordinatorLease(), data.namespaceEpochs(), data.overrides());
            persist();
            log.info("[FileControlStore] Membership updated to ringVersion={}, members={}",
                    membership.ringVersion(), membership.members());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CoordinatorLease getCoordinatorLease() {
        lock.lock();
        try {
            return data.coordinatorLease();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean acquireOrRenewCoordinatorLease(String candidateNodeId, Duration duration) {
        Objects.requireNonNull(candidateNodeId, "candidateNodeId must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        lock.lock();
        try {
            Instant currentTime = clock.instant();
            CoordinatorLease current = data.coordinatorLease();
            if (current == null || current.isExpired(currentTime) || Objects.equals(current.holderNodeId(), candidateNodeId)) {
                long nextVersion = current == null ? 1L : current.leaseVersion() + 1L;
                CoordinatorLease updated = new CoordinatorLease(candidateNodeId, currentTime, currentTime.plus(duration), nextVersion);
                this.data = new StateData(data.membership(), updated, data.namespaceEpochs(), data.overrides());
                persist();
                log.debug("[FileControlStore] Node '{}' acquired/renewed coordinator lease v{} until {}",
                        candidateNodeId, nextVersion, updated.expiresAt());
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void releaseCoordinatorLease(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        lock.lock();
        try {
            CoordinatorLease current = data.coordinatorLease();
            if (current != null && Objects.equals(current.holderNodeId(), nodeId)) {
                Instant currentTime = clock.instant();
                CoordinatorLease expired = new CoordinatorLease(nodeId, current.acquiredAt(), currentTime, current.leaseVersion() + 1L);
                this.data = new StateData(data.membership(), expired, data.namespaceEpochs(), data.overrides());
                persist();
                log.info("[FileControlStore] Node '{}' released coordinator lease", nodeId);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long getNamespaceEpoch(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        lock.lock();
        try {
            return data.namespaceEpochs().getOrDefault(namespaceId, 0L);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long advanceNamespaceEpoch(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        lock.lock();
        try {
            Map<String, Long> epochs = new HashMap<>(data.namespaceEpochs());
            long next = epochs.getOrDefault(namespaceId, 0L) + 1L;
            epochs.put(namespaceId, next);
            this.data = new StateData(data.membership(), data.coordinatorLease(), epochs, data.overrides());
            persist();
            return next;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<OverrideLeaseRecord> getOverride(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        lock.lock();
        try {
            OverrideLeaseRecord record = data.overrides().get(namespaceId);
            if (record != null && !record.isExpired(clock.instant())) {
                return Optional.of(record);
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setOverride(OverrideLeaseRecord override) {
        Objects.requireNonNull(override, "override must not be null");
        lock.lock();
        try {
            Map<String, OverrideLeaseRecord> overrides = new HashMap<>(data.overrides());
            overrides.put(override.namespaceId(), override);
            this.data = new StateData(data.membership(), data.coordinatorLease(), data.namespaceEpochs(), overrides);
            persist();
            log.info("[FileControlStore] Persisted override for namespace '{}' -> node '{}'",
                    override.namespaceId(), override.targetNodeId());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeOverride(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        lock.lock();
        try {
            Map<String, OverrideLeaseRecord> overrides = new HashMap<>(data.overrides());
            overrides.remove(namespaceId);
            this.data = new StateData(data.membership(), data.coordinatorLease(), data.namespaceEpochs(), overrides);
            persist();
            log.info("[FileControlStore] Removed override for namespace '{}'", namespaceId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<OverrideLeaseRecord> listOverrides() {
        lock.lock();
        try {
            Instant currentTime = clock.instant();
            return data.overrides().values().stream()
                    .filter(r -> !r.isExpired(currentTime))
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
