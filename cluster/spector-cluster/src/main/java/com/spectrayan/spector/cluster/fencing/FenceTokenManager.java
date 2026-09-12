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
package com.spectrayan.spector.cluster.fencing;

import com.spectrayan.spector.cluster.store.ControlStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Manages local fence token validation and minting from the control store (Req R2.1, R2.3, R2.4, R2.5, §5).
 *
 * <p>Enforces:
 * <ul>
 *   <li><b>Local validation (Req R2.4, Q7):</b> Validates against the owner node's local view, never a cached value.</li>
 *   <li><b>Zero I/O and zero allocation (Req §5):</b> Hot-path verification uses a local in-memory integer comparison.</li>
 *   <li><b>Metric counting (Req R9.2):</b> Tracks rejected fence attempts ({@code spector.route.fenced}).</li>
 * </ul>
 * </p>
 */
public class FenceTokenManager {

    private static final Logger log = LoggerFactory.getLogger(FenceTokenManager.class);

    private final ControlStore controlStore;
    private final ConcurrentHashMap<String, Long> localFences = new ConcurrentHashMap<>();
    private final LongAdder fenceRejections = new LongAdder();

    public FenceTokenManager(ControlStore controlStore) {
        this.controlStore = Objects.requireNonNull(controlStore, "controlStore must not be null");
    }

    /**
     * Updates or sets the local authoritative fence token for a namespace owned by this node.
     *
     * @param namespaceId target namespace
     * @param epoch       authoritative epoch
     */
    public void setLocalFence(String namespaceId, long epoch) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        localFences.put(namespaceId, epoch);
        log.debug("[FenceTokenManager] Set local fence for namespace '{}' to epoch {}", namespaceId, epoch);
    }

    /**
     * Checks if this node has an active local fence tracked for the given namespace.
     *
     * @param namespaceId target namespace
     * @return {@code true} if tracked; {@code false} otherwise
     */
    public boolean hasLocalFence(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        return localFences.containsKey(namespaceId);
    }

    /**
     * Returns the active local fence epoch for the namespace, or -1 if not actively tracked.
     *
     * @param namespaceId target namespace
     * @return active epoch or -1
     */
    public long getLocalFence(String namespaceId) {
        return localFences.getOrDefault(namespaceId, -1L);
    }

    /**
     * Removes the local fence when ownership is surrendered or moved away.
     *
     * @param namespaceId target namespace
     */
    public void removeLocalFence(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        localFences.remove(namespaceId);
        log.debug("[FenceTokenManager] Removed local fence for namespace '{}'", namespaceId);
    }

    /**
     * Mints a new fence token from the control store for promotion or epoch advancement (Req R2.1, R2.5).
     *
     * @param namespaceId target namespace
     * @return newly minted fence token
     */
    public FenceToken mintNewFence(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        long newEpoch = controlStore.advanceNamespaceEpoch(namespaceId);
        setLocalFence(namespaceId, newEpoch);
        log.info("[FenceTokenManager] Minted new fence token for namespace '{}' with epoch {}", namespaceId, newEpoch);
        return FenceToken.of(namespaceId, newEpoch);
    }

    /**
     * Mints a fence token for an already advanced epoch (Req R2.1).
     *
     * @param namespaceId target namespace
     * @param epoch       already advanced epoch
     * @return fence token
     */
    public FenceToken mintFenceForEpoch(String namespaceId, long epoch) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        setLocalFence(namespaceId, epoch);
        log.info("[FenceTokenManager] Minted fence token for namespace '{}' with epoch {}", namespaceId, epoch);
        return FenceToken.of(namespaceId, epoch);
    }

    /**
     * Allocation-free, I/O-free validation on the write path (Req R2.3, R2.4, §5).
     *
     * @param namespaceId   target namespace
     * @param incomingFence token carried on the write request
     * @return {@code true} if valid; {@code false} if mismatched or superseded (FENCED)
     */
    public boolean validateFence(String namespaceId, String incomingFence) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Long expected = localFences.get(namespaceId);
        if (expected == null) {
            // Namespace not tracked locally -> refuse
            fenceRejections.increment();
            return false;
        }
        if (incomingFence == null || incomingFence.isBlank()) {
            fenceRejections.increment();
            return false;
        }
        try {
            long incoming = Long.parseLong(incomingFence.trim());
            if (incoming == expected) {
                return true;
            }
        } catch (NumberFormatException ignored) {
            // Non-numeric token
        }
        fenceRejections.increment();
        return false;
    }

    /**
     * Returns total fence rejection count (Req R9.2, metric {@code spector.route.fenced}).
     *
     * @return total count of rejected fence requests
     */
    public long getFenceRejectionCount() {
        return fenceRejections.sum();
    }
}
