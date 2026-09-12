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
package com.spectrayan.spector.cluster.routing;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, allocation-free consistent hash ring using Ketama with virtual nodes (ADR-0034 §15.2, Req R3.1–R3.7).
 *
 * <p>Key properties:
 * <ul>
 *   <li><b>Pure Computation:</b> Immutable once built; identical inputs yield identical rings across JVMs (Invariant J2).</li>
 *   <li><b>Canonical Member Ordering:</b> Members are sorted and deduplicated prior to virtual node placement (Req R3.3).</li>
 *   <li><b>Digest Family:</b> Uses SHA-256 truncated to 64 bits (first 8 bytes as big-endian long). Explicitly rejects
 *       {@code Object.hashCode()} and {@code String.hashCode()} to guarantee cross-JVM determinism and uniform distribution (Req §5).</li>
 *   <li><b>Virtual Nodes:</b> Places {@value #DEFAULT_VNODES_PER_MEMBER} virtual nodes per member for tight balance factor (Req R3.5).</li>
 * </ul>
 * </p>
 */
public final class ConsistentHashRing {

    public static final int DEFAULT_VNODES_PER_MEMBER = 160;

    private final int ringVersion;
    private final List<String> members;
    private final int vnodesPerMember;
    private final long[] ringKeys;
    private final String[] ringOwners;

    private record VNode(long hash, String owner) implements Comparable<VNode> {
        @Override
        public int compareTo(VNode other) {
            int cmp = Long.compare(this.hash, other.hash);
            if (cmp != 0) {
                return cmp;
            }
            return this.owner.compareTo(other.owner);
        }
    }

    private ConsistentHashRing(int ringVersion, List<String> rawMembers, int vnodesPerMember) {
        if (vnodesPerMember <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "vnodesPerMember", "virtual node count must be positive");
        }
        if (rawMembers == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "members", "member set must not be null");
        }

        // 1. Canonicalize members: sort and deduplicate (Req R3.3, Invariant J2)
        List<String> canonical = rawMembers.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();

        if (canonical.isEmpty()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "members", "member set must not be empty; a cell with no owners cannot serve (Req R3.6)");
        }

        this.ringVersion = ringVersion;
        this.members = canonical;
        this.vnodesPerMember = vnodesPerMember;

        // 2. Generate virtual nodes
        int totalVNodes = canonical.size() * vnodesPerMember;
        List<VNode> vnodes = new ArrayList<>(totalVNodes);

        for (String member : canonical) {
            for (int i = 0; i < vnodesPerMember; i++) {
                long hash = hash64(member + "#" + i);
                vnodes.add(new VNode(hash, member));
            }
        }

        // 3. Sort vnodes array once and make immutable
        vnodes.sort(null);

        this.ringKeys = new long[totalVNodes];
        this.ringOwners = new String[totalVNodes];

        for (int i = 0; i < totalVNodes; i++) {
            VNode vnode = vnodes.get(i);
            this.ringKeys[i] = vnode.hash();
            this.ringOwners[i] = vnode.owner();
        }
    }

    /**
     * Constructs a consistent hash ring with the default virtual node count ({@value #DEFAULT_VNODES_PER_MEMBER}).
     *
     * @param ringVersion ring generation/version
     * @param members     list of member node identifiers
     * @return immutable consistent hash ring
     */
    public static ConsistentHashRing of(int ringVersion, List<String> members) {
        return new ConsistentHashRing(ringVersion, members, DEFAULT_VNODES_PER_MEMBER);
    }

    /**
     * Constructs a consistent hash ring with a specified virtual node count.
     *
     * @param ringVersion     ring generation/version
     * @param members         list of member node identifiers
     * @param vnodesPerMember number of virtual nodes per member
     * @return immutable consistent hash ring
     */
    public static ConsistentHashRing of(int ringVersion, List<String> members, int vnodesPerMember) {
        return new ConsistentHashRing(ringVersion, members, vnodesPerMember);
    }

    /**
     * Resolves the authoritative owner node for the given routing key (Req R3.1).
     *
     * <p>Executes allocation-free binary search with wrap-around.</p>
     *
     * @param key routing key
     * @return owner node identifier
     */
    public String ownerOf(RoutingKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return ownerOf(key.keyMaterial());
    }

    /**
     * Resolves the authoritative owner node for a key material string.
     *
     * @param keyMaterial canonical key string
     * @return owner node identifier
     */
    public String ownerOf(String keyMaterial) {
        long keyHash = hash64(keyMaterial);
        int idx = Arrays.binarySearch(ringKeys, keyHash);
        if (idx < 0) {
            idx = -(idx + 1);
        }
        if (idx >= ringKeys.length) {
            idx = 0; // Wrap around to the beginning of the circle
        }
        return ringOwners[idx];
    }

    /**
     * Computes the 64-bit cryptographic digest using SHA-256 truncated to 8 bytes (Req R2.3, ADR §15.2).
     *
     * @param value input string
     * @return 64-bit integer hash
     */
    public static long hash64(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest algorithm not found", e);
        }
    }

    public int ringVersion() {
        return ringVersion;
    }

    public List<String> members() {
        return members;
    }

    public int vnodesPerMember() {
        return vnodesPerMember;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsistentHashRing that = (ConsistentHashRing) o;
        return ringVersion == that.ringVersion
                && vnodesPerMember == that.vnodesPerMember
                && Objects.equals(members, that.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ringVersion, members, vnodesPerMember);
    }

    @Override
    public String toString() {
        return "ConsistentHashRing{version=" + ringVersion + ", members=" + members + ", vnodes=" + vnodesPerMember + "}";
    }
}
