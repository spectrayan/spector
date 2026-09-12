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

import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.membership.MembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative integration point for determining namespace ownership within a cell (ADR-0034 §15.2, Req R5, D3).
 *
 * <p>Implements the role matrix (Req R4.3, R5.1, D3):
 * <ul>
 *   <li><b>STANDALONE:</b> Short-circuits before membership or ring lookup; always owns locally (Invariant J4, Req R4.3).</li>
 *   <li><b>OWNER:</b> Consults the Ketama hash ring; {@link #ownsLocally(RoutingKey)} compares the ring's owner to {@code nodeId}.</li>
 *   <li><b>REPLICA:</b> Refuses ownership locally ({@code ownsLocally = false}); {@link #resolve(RoutingKey)} returns the ring owner.</li>
 *   <li><b>GATEWAY:</b> Refuses ownership locally ({@code ownsLocally = false}); {@link #resolve(RoutingKey)} returns the ring owner for routing/refusal.</li>
 * </ul>
 * </p>
 */
public final class OwnershipResolver {

    private static final Logger log = LoggerFactory.getLogger(OwnershipResolver.class);

    private final NodeIdentity identity;
    private final MembershipSource membershipSource;
    private final java.util.concurrent.atomic.AtomicReference<ConsistentHashRing> ringRef = new java.util.concurrent.atomic.AtomicReference<>();
    private final OverrideLeaseManager overrideLeaseManager;

    /**
     * Creates an ownership resolver with a standalone identity (bypassing the ring).
     *
     * @return standalone ownership resolver
     */
    public static OwnershipResolver standalone() {
        return new OwnershipResolver(NodeIdentity.standalone(), null);
    }

    /**
     * Constructs an ownership resolver for the given identity and optional membership source.
     *
     * @param identity         node identity (role, cellId, nodeId)
     * @param membershipSource membership source (required if role is not {@link NodeRole#STANDALONE})
     * @throws SpectorValidationException if role is not standalone and membership is null or empty (Req R7.3, L2)
     */
    public OwnershipResolver(NodeIdentity identity, MembershipSource membershipSource) {
        this(identity, membershipSource, null);
    }

    /**
     * Constructs an ownership resolver with override lease support (Req R3.1).
     *
     * @param identity             node identity (role, cellId, nodeId)
     * @param membershipSource     membership source
     * @param overrideLeaseManager manager tracking active namespace override leases
     */
    public OwnershipResolver(
            NodeIdentity identity,
            MembershipSource membershipSource,
            OverrideLeaseManager overrideLeaseManager) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.overrideLeaseManager = overrideLeaseManager;
        if (identity.role() == NodeRole.STANDALONE) {
            this.membershipSource = membershipSource;
            log.info("Initialized OwnershipResolver in STANDALONE mode (owns all namespaces, ring bypassed)");
        } else {
            this.membershipSource = Objects.requireNonNull(membershipSource,
                    "membershipSource must not be null when node role is " + identity.role());
            CellMembership membership = membershipSource.current();
            if (membership == null || membership.members().isEmpty()) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                        "membership", "membership must not be empty for role " + identity.role() + " (Req R7.3, L2)");
            }
            ConsistentHashRing ring = ConsistentHashRing.of(membership.ringVersion(), membership.members());
            this.ringRef.set(ring);
            log.info("Initialized OwnershipResolver for cell '{}', node '{}', role '{}' with ring version {} and members: {}",
                    identity.cellId(), identity.nodeId(), identity.role(), ring.ringVersion(), ring.members());
        }
    }

    /**
     * Atomically reloads the hash ring with updated cell membership without node restart (Req R5.1, R5.2).
     *
     * @param newMembership new membership snapshot
     */
    public void reloadRing(CellMembership newMembership) {
        if (identity.role() == NodeRole.STANDALONE) {
            return;
        }
        Objects.requireNonNull(newMembership, "newMembership must not be null");
        if (newMembership.members().isEmpty()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "membership", "membership must not be empty for role " + identity.role());
        }
        ConsistentHashRing newRing = ConsistentHashRing.of(newMembership.ringVersion(), newMembership.members());
        ringRef.set(newRing);
        log.info("[OwnershipResolver] Atomically reloaded hash ring for cell '{}' to version {} with members: {}",
                identity.cellId(), newMembership.ringVersion(), newMembership.members());
    }

    /**
     * Atomically reloads the hash ring from the configured membership source without node restart (Req R5.1, R5.2).
     */
    public void reloadRingFromSource() {
        if (identity.role() == NodeRole.STANDALONE || membershipSource == null) {
            return;
        }
        CellMembership current = membershipSource.current();
        if (current != null) {
            reloadRing(current);
        }
    }

    /**
     * Determines whether the current node authoritatively owns the specified routing key locally (Req R5.1, R3.1).
     *
     * @param key routing key
     * @return {@code true} if this node owns the key locally; {@code false} otherwise
     */
    public boolean ownsLocally(RoutingKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return switch (identity.role()) {
            case STANDALONE -> true;
            case OWNER -> {
                if (overrideLeaseManager != null) {
                    var overrideOpt = overrideLeaseManager.getOverride(key.namespaceId());
                    if (overrideOpt.isPresent()) {
                        yield Objects.equals(identity.nodeId(), overrideOpt.get().targetNodeId());
                    }
                }
                ConsistentHashRing ring = ringRef.get();
                String owner = ring != null ? ring.ownerOf(key) : null;
                yield Objects.equals(identity.nodeId(), owner);
            }
            case REPLICA, GATEWAY -> false;
        };
    }

    /**
     * Resolves the full authoritative route binding for the specified routing key (Req R5.5, R3.1).
     *
     * @param key routing key
     * @return route binding containing the owner node identifier and ring epoch
     */
    public RouteBinding resolve(RoutingKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return switch (identity.role()) {
            case STANDALONE -> RouteBinding.ofHash(
                    key,
                    identity.nodeId() != null ? identity.nodeId() : "standalone",
                    0L
            );
            case OWNER, REPLICA, GATEWAY -> {
                if (overrideLeaseManager != null) {
                    var overrideOpt = overrideLeaseManager.getOverride(key.namespaceId());
                    if (overrideOpt.isPresent()) {
                        var override = overrideOpt.get();
                        yield RouteBinding.ofOverride(
                                key,
                                override.targetNodeId(),
                                override.epoch(),
                                override.fence()
                        );
                    }
                }
                ConsistentHashRing ring = ringRef.get();
                yield RouteBinding.ofHash(
                        key,
                        ring != null ? ring.ownerOf(key) : identity.nodeId(),
                        ring != null ? ring.ringVersion() : 0L
                );
            }
        };
    }

    /**
     * Returns optional override lease manager.
     */
    public Optional<OverrideLeaseManager> overrideLeaseManager() {
        return Optional.ofNullable(overrideLeaseManager);
    }

    /**
     * Returns the node identity.
     *
     * @return node identity
     */
    public NodeIdentity identity() {
        return identity;
    }

    /**
     * Returns the underlying hash ring, if active.
     *
     * @return optional containing the hash ring, or empty if standalone
     */
    public Optional<ConsistentHashRing> ring() {
        return Optional.ofNullable(ringRef.get());
    }
}
