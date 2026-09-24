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
package com.spectrayan.spector.synapse.catalog;

import com.spectrayan.spector.memory.policy.DeletionRequest;
import com.spectrayan.spector.memory.policy.MutationPolicy;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceLegalHoldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Enforces legal hold on engine deletions by consulting the {@link AccountCatalog}.
 *
 * <p>This is the enterprise implementation of the engine's {@link MutationPolicy} seam. The engine cannot see
 * the catalog — it must not, or an embedded library would drag a control-plane dependency with it — so it asks,
 * and this answers.</p>
 *
 * <h2>Why this is installed on the engine rather than checked in a controller</h2>
 * <p>Before this, legal hold was enforced only in {@code TenantErasureService} and
 * {@code JdbcAccountCatalog}, both of which sit on the namespace-erasure path. Record deletion reached the
 * engine through five other routes — the REST controller, its bulk endpoint, MCP tools, the embedded Java
 * API and both chat-memory bulk paths — and none of them passed through a hold check. Installing the policy
 * on the engine covers all of them at once, including routes added later.</p>
 *
 * <h2>Granularity: namespace, not record</h2>
 * <p>Hold is recorded on {@code NamespaceRecord.legalHold}. No record-level hold exists as data anywhere in
 * the system, so this refuses <b>every</b> deletion in a held namespace and cannot protect an individual
 * record within an unheld one. That is the honest extent of the mechanism and is stated here rather than
 * implied by its absence.</p>
 *
 * <h2>Fail closed</h2>
 * <p>An unresolvable namespace is refused, not permitted. It is not an absent hold — it is an unknown one,
 * and treating unknown as permitted is how held data gets destroyed. This matches the position
 * {@code TenantErasureService} already takes for namespace erasure.</p>
 *
 * <h2>{@code FORGET} is allowed through</h2>
 * <p>{@link com.spectrayan.spector.memory.policy.DeletionKind#FORGET} only sets a tombstone bit: the payload
 * bytes remain on disk and in every backup, so data under hold stays available for discovery. Refusing it
 * would break the idempotent-delete contract Spring AI's {@code VectorStore} and both chat-memory bulk paths
 * depend on, for no gain in retention. Destructive kinds — {@code PURGE}, {@code VACUUM},
 * {@code ERASE_NAMESPACE} — are refused.</p>
 *
 * <p>This asymmetry is a deliberate trade and is the one place a reader might expect stricter behaviour, so
 * it is spelled out: a held namespace can still have records hidden, but not destroyed.</p>
 */
public final class CatalogMutationPolicy implements MutationPolicy {

    private static final Logger log = LoggerFactory.getLogger(CatalogMutationPolicy.class);

    private final AccountCatalog catalog;
    private final String ownerAccountId;

    /**
     * Creates a policy scoped to one namespace's owning account.
     *
     * <p>Scoped rather than global because {@link AccountCatalog#resolve} is keyed by
     * {@code (accountId, slugOrId)} and the engine knows only its namespace id. Binding the owner at
     * construction keeps the lookup exact and avoids adding a namespace-id-only lookup to the catalog API —
     * which every implementation would then have to satisfy, some by scanning.</p>
     *
     * @param catalog        the catalog holding the authoritative legal-hold state
     * @param ownerAccountId the account owning the namespace this policy guards
     */
    public CatalogMutationPolicy(AccountCatalog catalog, String ownerAccountId) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.ownerAccountId = Objects.requireNonNull(ownerAccountId, "ownerAccountId");
    }

    @Override
    public void checkDeletion(DeletionRequest request) {
        if (!request.kind().destructive()) {
            // FORGET destroys nothing — see the class javadoc for why this is allowed under hold.
            return;
        }
        String namespaceId = request.namespaceId();
        Optional<NamespaceRecord> recordOpt;
        try {
            recordOpt = catalog.resolve(ownerAccountId, namespaceId);
        } catch (RuntimeException e) {
            log.error("[DeletionPolicy] Refusing {} of '{}' in namespace '{}': the catalog could not be "
                            + "consulted, so legal hold cannot be ruled out (fail-closed)",
                    request.kind(), request.memoryId(), namespaceId, e);
            throw new IllegalStateException(
                    "Cannot verify legal hold for namespace '" + namespaceId + "'; refusing "
                            + request.kind(), e);
        }
        if (recordOpt.isEmpty()) {
            // Unknown is not the same as unheld.
            log.error("[DeletionPolicy] Refusing {} of '{}': namespace '{}' does not resolve in the catalog, "
                            + "so legal hold cannot be verified (fail-closed)",
                    request.kind(), request.memoryId(), namespaceId);
            throw new IllegalStateException(
                    "Cannot verify legal hold for unresolvable namespace '" + namespaceId + "'; refusing "
                            + request.kind());
        }
        NamespaceRecord record = recordOpt.get();
        if (record.legalHold()) {
            log.warn("[DeletionPolicy] Refusing {} of '{}': namespace '{}' is under active legal hold",
                    request.kind(), request.memoryId(), record.namespaceId());
            throw new NamespaceLegalHoldException(record.namespaceId());
        }
    }

    @Override
    public String toString() {
        return "CatalogMutationPolicy[account=" + ownerAccountId + ", namespace-granular legal hold]";
    }
}
