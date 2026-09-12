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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service Provider Interface (SPI) for the authoritative cluster control store (Req R1.1, D1).
 *
 * <p>Holds the cell member set, ring version, coordinator lease, monotonic namespace epochs, and
 * active override leases. The control store is the single source of truth for topology changes and
 * fencing tokens.</p>
 */
public interface ControlStore {

    /**
     * Returns the authoritative cell membership snapshot.
     *
     * @return current cell membership, or {@code null} if uninitialized
     */
    CellMembership getMembership();

    /**
     * Updates the authoritative cell membership snapshot and bumps the ring version.
     *
     * @param membership new cell membership
     */
    void updateMembership(CellMembership membership);

    /**
     * Returns the current coordinator lease, or {@code null} if no coordinator has ever been elected.
     *
     * @return coordinator lease
     */
    CoordinatorLease getCoordinatorLease();

    /**
     * Attempts to acquire or renew the cell coordinator lease for the given candidate node.
     *
     * <p>Acquisition succeeds if:
     * <ul>
     *   <li>No lease currently exists</li>
     *   <li>The existing lease is expired according to the store's clock (Req R1.5)</li>
     *   <li>The existing lease is already held by {@code candidateNodeId} (renewal)</li>
     * </ul>
     * </p>
     *
     * @param candidateNodeId node attempting acquisition or renewal
     * @param duration        duration of the lease
     * @return {@code true} if acquired or renewed; {@code false} if held by another active node
     */
    boolean acquireOrRenewCoordinatorLease(String candidateNodeId, Duration duration);

    /**
     * Explicitly releases the coordinator lease held by {@code nodeId}.
     *
     * @param nodeId node releasing the lease
     */
    void releaseCoordinatorLease(String nodeId);

    /**
     * Returns the current monotonic epoch for the given namespace. Defaults to 0 if never set.
     *
     * @param namespaceId namespace identifier
     * @return current epoch
     */
    long getNamespaceEpoch(String namespaceId);

    /**
     * Atomically increments and returns the next monotonic epoch for the given namespace (Req R2.1, Q3).
     *
     * @param namespaceId namespace identifier
     * @return newly advanced epoch
     */
    long advanceNamespaceEpoch(String namespaceId);

    /**
     * Retrieves the active override lease for the given namespace, if present and not expired.
     *
     * @param namespaceId namespace identifier
     * @return optional containing the active override record if present
     */
    Optional<OverrideLeaseRecord> getOverride(String namespaceId);

    /**
     * Sets or updates an override lease for a namespace (Req R3.1).
     *
     * @param override override lease record
     */
    void setOverride(OverrideLeaseRecord override);

    /**
     * Removes an active override lease for a namespace (Req R3.2).
     *
     * @param namespaceId namespace identifier
     */
    void removeOverride(String namespaceId);

    /**
     * Lists all active (non-expired) override leases currently recorded in the control store (Req R3.6).
     *
     * @return list of active override records
     */
    List<OverrideLeaseRecord> listOverrides();

    /**
     * Returns the current authoritative timestamp of the control store (Req R1.5).
     *
     * @return authoritative instant
     */
    Instant now();
}
