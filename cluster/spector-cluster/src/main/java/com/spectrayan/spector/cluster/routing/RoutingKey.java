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

import java.util.Objects;

/**
 * Immutable key identifying a namespace on the cluster ownership ring (ADR-0034 §15.2, Req R2.1–R2.5).
 *
 * <p>Key material: {@code (tenantId != null ? tenantId : NULL_TENANT_SENTINEL) + "/" + namespaceId}.</p>
 *
 * <p><b>Important architectural constraints:</b>
 * <ul>
 *   <li>{@code accountId} is intentionally omitted: multiple accounts can share a namespace
 *       ({@code NamespaceType.SHARED}), so an account-keyed ring would double-own shared namespaces (Req R2.1).</li>
 *   <li>{@code tenantId} is nullable for untenanted / solo / OSS namespaces, hashing deterministically (Req R2.2).</li>
 * </ul>
 * </p>
 *
 * @param cellId      the cell identifier (optional for standalone, required for cluster routing)
 * @param tenantId    the tenant identifier, or {@code null} for untenanted namespaces
 * @param namespaceId the global namespace identifier (TSID or unique name)
 */
public record RoutingKey(String cellId, String tenantId, String namespaceId) {

    public static final String NULL_TENANT_SENTINEL = "__NULL_TENANT__";

    public RoutingKey {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        ClusterValidation.validateNamespaceId(namespaceId);
        if (tenantId != null) {
            ClusterValidation.validateTenantId(tenantId);
        }
    }

    /**
     * Creates an untenanted routing key within a cell.
     *
     * @param cellId      the cell identifier
     * @param namespaceId the namespace identifier
     * @return new untenanted routing key
     */
    public static RoutingKey ofUntenanted(String cellId, String namespaceId) {
        return new RoutingKey(cellId, null, namespaceId);
    }

    /**
     * Creates a tenanted routing key within a cell.
     *
     * @param cellId      the cell identifier
     * @param tenantId    the tenant identifier
     * @param namespaceId the namespace identifier
     * @return new tenanted routing key
     */
    public static RoutingKey ofTenanted(String cellId, String tenantId, String namespaceId) {
        return new RoutingKey(cellId, tenantId, namespaceId);
    }

    /**
     * Computes the canonical key material fed into the consistent hash ring (ADR §15.2, Req R2.4).
     *
     * @return canonical key string {@code "{tenantId|__NULL_TENANT__}/{namespaceId}"}
     */
    public String keyMaterial() {
        return (tenantId != null ? tenantId : NULL_TENANT_SENTINEL) + "/" + namespaceId;
    }

    /**
     * Emits the canonical Redis Cluster key with literal hash tag:
     * {@code "rt:ns:{" + cell + ":" + tenant + ":" + namespaceId + "}"}
     * per ADR-0034 §15.2, Req R2.2, and Decision B2.
     *
     * @return canonical Redis hash key
     */
    public String redisHashKey() {
        return redisHashKey(cellId != null ? cellId : "default");
    }

    /**
     * Emits the canonical Redis Cluster key for a specific cell override.
     *
     * @param effectiveCellId the cell identifier
     * @return canonical Redis hash key with hash tag
     */
    public String redisHashKey(String effectiveCellId) {
        String cell = (effectiveCellId != null && !effectiveCellId.isBlank()) ? effectiveCellId : "default";
        String tenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : NULL_TENANT_SENTINEL;
        return "rt:ns:{" + cell + ":" + tenant + ":" + namespaceId + "}";
    }

    /**
     * Reserved key format for Phase 3 replica freshness hints (Req R2.4).
     * Must NOT be written in Phase 2.
     *
     * @return reserved Redis hint key
     */
    public String redisHintKey() {
        return redisHintKey(cellId != null ? cellId : "default");
    }

    /**
     * Reserved key format for Phase 3 replica freshness hints for a specific cell override.
     *
     * @param effectiveCellId the cell identifier
     * @return reserved Redis hint key
     */
    public String redisHintKey(String effectiveCellId) {
        return redisHashKey(effectiveCellId) + ":hint";
    }

    /**
     * Emits the canonical key for published cell ring metadata:
     * {@code "rt:cell:{" + cell + "}:ring"} per ADR-0034 §8.3 and Design §3.
     *
     * @param cellId the cell identifier
     * @return Redis key for the ring metadata
     */
    public static String redisCellRingKey(String cellId) {
        String cell = (cellId != null && !cellId.isBlank()) ? cellId : "default";
        return "rt:cell:{" + cell + "}:ring";
    }

    /**
     * Emits the canonical pub/sub invalidation channel name for a cell:
     * {@code "rt:cell:{" + cell + "}:invalidation"} per Req R4.1.
     *
     * @param cellId the cell identifier
     * @return Redis invalidation channel name
     */
    public static String redisInvalidationChannel(String cellId) {
        String cell = (cellId != null && !cellId.isBlank()) ? cellId : "default";
        return "rt:cell:{" + cell + "}:invalidation";
    }
}
