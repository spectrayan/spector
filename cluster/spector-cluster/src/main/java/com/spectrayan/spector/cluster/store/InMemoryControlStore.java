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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, in-memory implementation of {@link ControlStore} for unit testing, development,
 * and single-process simulation (Req R1.1, R1.7).
 *
 * <p>Uses {@link ReentrantLock} to ensure virtual thread compatibility without thread pinning,
 * and supports custom {@link Clock} injection for deterministic chaos and time-travel testing.</p>
 */
public class InMemoryControlStore implements ControlStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryControlStore.class);

    private final ReentrantLock lock = new ReentrantLock();
    private final Clock clock;

    private CellMembership membership;
    private CoordinatorLease coordinatorLease;
    private final Map<String, Long> namespaceEpochs = new HashMap<>();
    private final Map<String, OverrideLeaseRecord> overrides = new HashMap<>();

    public InMemoryControlStore() {
        this(Clock.systemUTC());
    }

    public InMemoryControlStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public CellMembership getMembership() {
        lock.lock();
        try {
            return membership;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void updateMembership(CellMembership membership) {
        Objects.requireNonNull(membership, "membership must not be null");
        lock.lock();
        try {
            this.membership = membership;
            log.info("[InMemoryControlStore] Membership updated to ringVersion={}, members={}",
                    membership.ringVersion(), membership.members());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CoordinatorLease getCoordinatorLease() {
        lock.lock();
        try {
            return coordinatorLease;
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
            if (coordinatorLease == null || coordinatorLease.isExpired(currentTime)
                    || Objects.equals(coordinatorLease.holderNodeId(), candidateNodeId)) {
                long nextVersion = coordinatorLease == null ? 1L : coordinatorLease.leaseVersion() + 1L;
                coordinatorLease = new CoordinatorLease(candidateNodeId, currentTime, currentTime.plus(duration), nextVersion);
                log.debug("[InMemoryControlStore] Node '{}' acquired/renewed coordinator lease v{} until {}",
                        candidateNodeId, nextVersion, coordinatorLease.expiresAt());
                return true;
            }
            log.debug("[InMemoryControlStore] Node '{}' rejected for lease; currently held by '{}' until {}",
                    candidateNodeId, coordinatorLease.holderNodeId(), coordinatorLease.expiresAt());
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
            if (coordinatorLease != null && Objects.equals(coordinatorLease.holderNodeId(), nodeId)) {
                Instant currentTime = clock.instant();
                coordinatorLease = new CoordinatorLease(nodeId, coordinatorLease.acquiredAt(), currentTime,
                        coordinatorLease.leaseVersion() + 1L);
                log.info("[InMemoryControlStore] Node '{}' released coordinator lease", nodeId);
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
            return namespaceEpochs.getOrDefault(namespaceId, 0L);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long advanceNamespaceEpoch(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        lock.lock();
        try {
            long next = namespaceEpochs.getOrDefault(namespaceId, 0L) + 1L;
            namespaceEpochs.put(namespaceId, next);
            log.debug("[InMemoryControlStore] Advanced epoch for namespace '{}' to {}", namespaceId, next);
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
            OverrideLeaseRecord record = overrides.get(namespaceId);
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
            overrides.put(override.namespaceId(), override);
            log.info("[InMemoryControlStore] Set override for namespace '{}' -> node '{}', epoch={}, expiresAt={}",
                    override.namespaceId(), override.targetNodeId(), override.epoch(), override.expiresAt());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeOverride(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        lock.lock();
        try {
            overrides.remove(namespaceId);
            log.info("[InMemoryControlStore] Removed override for namespace '{}'", namespaceId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<OverrideLeaseRecord> listOverrides() {
        lock.lock();
        try {
            Instant currentTime = clock.instant();
            return overrides.values().stream()
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
