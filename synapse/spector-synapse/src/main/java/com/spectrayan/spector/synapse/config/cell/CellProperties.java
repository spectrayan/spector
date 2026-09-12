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
package com.spectrayan.spector.synapse.config.cell;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.membership.MembershipSource;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.config.SpectorPropertyConstants;

import java.io.Serializable;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Cell High Availability, Node Identity, and the Ownership Ring (ADR-0034, Req R4, R8).
 *
 * <p>Prefix: {@code spector.cell.*}</p>
 */
public class CellProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String role = SpectorPropertyConstants.DEFAULT_CELL_ROLE;
    private String nodeId;
    private RingProperties ring = new RingProperties();

    public CellProperties() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        if (role != null && !role.isBlank()) {
            this.role = role.trim();
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public RingProperties getRing() {
        return ring;
    }

    public void setRing(RingProperties ring) {
        if (ring != null) {
            this.ring = ring;
        }
    }

    /**
     * Resolves the {@link NodeRole} enum from the configured string.
     *
     * @return node operational role
     */
    public NodeRole resolvedRole() {
        return NodeRole.fromString(this.role);
    }

    /**
     * Resolves the effective node ID. If {@code nodeId} is explicitly configured, it is used;
     * otherwise, it defaults to the pod hostname (Req R4.5).
     *
     * @return effective node identifier
     */
    public String resolveEffectiveNodeId() {
        if (nodeId != null && !nodeId.isBlank()) {
            return nodeId.trim();
        }
        return resolveHostname();
    }

    /**
     * Converts these configuration properties into an authoritative {@link NodeIdentity} (Req R4).
     *
     * @return node identity
     */
    public NodeIdentity toNodeIdentity() {
        NodeRole resolvedRole = resolvedRole();
        if (resolvedRole == NodeRole.STANDALONE) {
            return new NodeIdentity(id, nodeId != null && !nodeId.isBlank() ? nodeId : null, NodeRole.STANDALONE);
        }
        return new NodeIdentity(id, resolveEffectiveNodeId(), resolvedRole);
    }

    /**
     * Constructs the {@link MembershipSource} defined by configuration (Req R7).
     *
     * @return membership source or {@code null} if role is standalone and no members configured
     */
    public MembershipSource toMembershipSource() {
        if (ring.getMembersFile() != null && !ring.getMembersFile().isBlank()) {
            return new StaticMembershipSource(id, ring.getVersion(), Path.of(ring.getMembersFile()));
        }
        if (ring.getMembers() != null && !ring.getMembers().isEmpty()) {
            return new StaticMembershipSource(id, ring.getVersion(), ring.getMembers());
        }
        return null;
    }

    /**
     * Instantiates an {@link OwnershipResolver} based on these cell properties (Req R5, D3).
     *
     * @return ownership resolver
     */
    public OwnershipResolver toOwnershipResolver() {
        NodeIdentity identity = toNodeIdentity();
        if (identity.role() == NodeRole.STANDALONE) {
            return new OwnershipResolver(identity, null);
        }
        MembershipSource source = toMembershipSource();
        return new OwnershipResolver(identity, source);
    }

    public static String resolveHostname() {
        String envHostname = System.getenv("HOSTNAME");
        if (envHostname != null && !envHostname.isBlank()) {
            return envHostname.trim();
        }
        String envHost = System.getenv("HOST");
        if (envHost != null && !envHost.isBlank()) {
            return envHost.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    /**
     * Nested properties for {@code spector.cell.ring.*}.
     */
    public static class RingProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private int version = SpectorPropertyConstants.DEFAULT_CELL_RING_VERSION;
        private List<String> members = new ArrayList<>();
        private String membersFile;

        public RingProperties() {}

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public List<String> getMembers() {
            return members;
        }

        public void setMembers(List<String> members) {
            this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
        }

        public String getMembersFile() {
            return membersFile;
        }

        public void setMembersFile(String membersFile) {
            this.membersFile = membersFile;
        }
    }
}
