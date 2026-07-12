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
package com.spectrayan.spector.cluster;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Namespace-level affinity router for multi-pod deployments.
 *
 * <h3>Purpose</h3>
 * <p>Deterministically maps {@code tenantId:namespaceId} compound keys to
 * a specific pod ordinal using a consistent hash ring. This ensures that
 * all requests for a given user/namespace are always routed to the same
 * pod — the <em>owner</em> — eliminating the need for distributed locks
 * on per-user memory-mapped files.</p>
 *
 * <h3>Consistent Hashing</h3>
 * <p>Uses the same MD5-based hashing as {@link ConsistentHashShardManager}
 * to place virtual nodes on a {@link ConcurrentSkipListMap} ring. Each
 * pod ordinal (0..N-1) gets {@code virtualNodesPerPod} positions on the
 * ring, ensuring balanced distribution even with small pod counts.</p>
 *
 * <h3>Topology Changes</h3>
 * <p>When pods scale up or down, {@link #resize(int)} rebuilds the ring.
 * Consistent hashing guarantees that only ~1/N of namespaces change
 * ownership on resize, minimizing rebalancing overhead.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>All ring lookups use a {@link ReentrantReadWriteLock}. Reads
 * ({@link #ownerOf}) acquire the read lock; mutations ({@link #resize})
 * acquire the write lock. Safe for concurrent use from virtual threads.</p>
 *
 * @see ConsistentHashShardManager for document-level sharding
 */
public final class NamespaceAffinityRouter {

    private static final Logger log = LoggerFactory.getLogger(NamespaceAffinityRouter.class);

    /** Default number of virtual nodes per physical pod on the hash ring. */
    public static final int DEFAULT_VIRTUAL_NODES_PER_POD = 150;

    /** Maximum supported pod count. */
    public static final int MAX_POD_COUNT = 256;

    private final int virtualNodesPerPod;

    /** The consistent hash ring: hash position → pod ordinal. */
    private final ConcurrentSkipListMap<Long, Integer> ring;

    /** Read-write lock protecting ring reads and mutations. */
    private final ReentrantReadWriteLock ringLock;

    /** Current number of pods in the ring. */
    private final AtomicInteger podCount;

    /** This pod's ordinal (0-based). */
    private final int myOrdinal;

    /**
     * Creates a namespace affinity router for a specific pod.
     *
     * @param myOrdinal the ordinal of this pod (0-based, from StatefulSet)
     * @param podCount  the total number of leader pods in the cell
     * @throws SpectorValidationException if myOrdinal is negative or podCount is invalid
     */
    public NamespaceAffinityRouter(int myOrdinal, int podCount) {
        this(myOrdinal, podCount, DEFAULT_VIRTUAL_NODES_PER_POD);
    }

    /**
     * Creates a namespace affinity router with custom virtual node count.
     *
     * @param myOrdinal          the ordinal of this pod (0-based)
     * @param podCount           the total number of leader pods
     * @param virtualNodesPerPod number of virtual nodes per pod on the ring
     * @throws SpectorValidationException if parameters are invalid
     */
    public NamespaceAffinityRouter(int myOrdinal, int podCount, int virtualNodesPerPod) {
        if (myOrdinal < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "myOrdinal", 0, MAX_POD_COUNT - 1, myOrdinal);
        }
        if (podCount < 1 || podCount > MAX_POD_COUNT) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "podCount", 1, MAX_POD_COUNT, podCount);
        }
        if (myOrdinal >= podCount) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "myOrdinal (" + myOrdinal + ") must be < podCount (" + podCount + ")");
        }
        if (virtualNodesPerPod < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "virtualNodesPerPod", 1, Integer.MAX_VALUE, virtualNodesPerPod);
        }

        this.myOrdinal = myOrdinal;
        this.virtualNodesPerPod = virtualNodesPerPod;
        this.ring = new ConcurrentSkipListMap<>();
        this.ringLock = new ReentrantReadWriteLock();
        this.podCount = new AtomicInteger(podCount);

        buildRing(podCount);

        log.info("[AffinityRouter] Initialized: myOrdinal={}, podCount={}, virtualNodes={}",
                myOrdinal, podCount, virtualNodesPerPod);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Core Routing
    // ═══════════════════════════════════════════════════════════════

    /**
     * Computes the owner pod ordinal for a namespace.
     *
     * <p>The compound key {@code tenantId:namespaceId} is hashed and
     * mapped to the nearest virtual node on the consistent hash ring.
     * The associated pod ordinal is the namespace's owner.</p>
     *
     * @param tenantId    the tenant identifier
     * @param namespaceId the namespace identifier (typically userId)
     * @return the pod ordinal (0-based) that owns this namespace
     * @throws SpectorValidationException if tenantId or namespaceId is blank
     */
    public int ownerOf(String tenantId, String namespaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");

        String compoundKey = tenantId + ":" + namespaceId;
        long hash = ConsistentHashShardManager.hash(compoundKey);

        ringLock.readLock().lock();
        try {
            Map.Entry<Long, Integer> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                // Wrap around to first entry in the ring
                entry = ring.firstEntry();
            }
            return entry.getValue();
        } finally {
            ringLock.readLock().unlock();
        }
    }

    /**
     * Checks whether this pod is the owner of the given namespace.
     *
     * <p>Equivalent to {@code ownerOf(tenantId, namespaceId) == myOrdinal}
     * but expressed as a convenience for the common routing check in
     * Armeria filters.</p>
     *
     * @param tenantId    the tenant identifier
     * @param namespaceId the namespace identifier
     * @return true if this pod owns the namespace
     */
    public boolean isLocal(String tenantId, String namespaceId) {
        return ownerOf(tenantId, namespaceId) == myOrdinal;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Topology Management
    // ═══════════════════════════════════════════════════════════════

    /**
     * Rebuilds the hash ring for a new pod count.
     *
     * <p>Called when the StatefulSet scales up or down. Consistent hashing
     * guarantees that only ~1/N of namespaces change ownership, where N
     * is the new pod count.</p>
     *
     * @param newPodCount the new total number of leader pods
     * @throws SpectorValidationException if newPodCount is invalid
     */
    public void resize(int newPodCount) {
        if (newPodCount < 1 || newPodCount > MAX_POD_COUNT) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "newPodCount", 1, MAX_POD_COUNT, newPodCount);
        }

        int oldCount = podCount.get();
        if (newPodCount == oldCount) {
            log.debug("[AffinityRouter] resize({}) — no change", newPodCount);
            return;
        }

        ringLock.writeLock().lock();
        try {
            ring.clear();
            buildRingUnsafe(newPodCount);
            podCount.set(newPodCount);
            log.info("[AffinityRouter] Resized ring: {} → {} pods ({} virtual nodes total)",
                    oldCount, newPodCount, ring.size());
        } finally {
            ringLock.writeLock().unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Observability
    // ═══════════════════════════════════════════════════════════════

    /** Returns this pod's ordinal. */
    public int myOrdinal() {
        return myOrdinal;
    }

    /** Returns the current pod count. */
    public int podCount() {
        return podCount.get();
    }

    /** Returns the number of virtual nodes on the ring. */
    public int ringSize() {
        return ring.size();
    }

    /**
     * Returns the distribution of virtual nodes across pod ordinals.
     *
     * <p>Useful for verifying balanced distribution. Each entry maps
     * a pod ordinal to the fraction of the ring it owns.</p>
     *
     * @return unmodifiable map of pod ordinal → ownership fraction
     */
    public Map<Integer, Double> distribution() {
        ringLock.readLock().lock();
        try {
            int total = ring.size();
            if (total == 0) {
                return Collections.emptyMap();
            }

            var counts = new java.util.HashMap<Integer, Integer>();
            for (int ordinal : ring.values()) {
                counts.merge(ordinal, 1, Integer::sum);
            }

            var result = new java.util.HashMap<Integer, Double>();
            for (var entry : counts.entrySet()) {
                result.put(entry.getKey(), (double) entry.getValue() / total);
            }
            return Collections.unmodifiableMap(result);
        } finally {
            ringLock.readLock().unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Private Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Builds the hash ring (acquires write lock internally).
     */
    private void buildRing(int count) {
        ringLock.writeLock().lock();
        try {
            buildRingUnsafe(count);
        } finally {
            ringLock.writeLock().unlock();
        }
    }

    /**
     * Builds the hash ring (caller must hold write lock).
     */
    private void buildRingUnsafe(int count) {
        for (int ordinal = 0; ordinal < count; ordinal++) {
            for (int vnode = 0; vnode < virtualNodesPerPod; vnode++) {
                long hash = ConsistentHashShardManager.hash(
                        "pod-" + ordinal + "-ns-vnode-" + vnode);
                ring.put(hash, ordinal);
            }
        }
    }
}
