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
package com.spectrayan.spector.memory.policy;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Mutation policy that enforces explicit write destination scoping.
 *
 * <p>When enabled, rejects any {@code REMEMBER} operation where the caller relied on
 * a defaulted namespace (the engine-wide fallback) rather than explicitly specifying one.
 * This prevents accidental cross-contamination in multi-agent and multi-tenant deployments
 * where each agent should write only to its own scoped namespace.</p>
 *
 * <h3>Design Note</h3>
 * <p>Tier (memory type) validation is handled at the API boundary (MCP tool / REST controller)
 * rather than here, because at the engine level every {@code remember()} call already carries
 * an explicit {@code MemoryType} parameter — the defaulting to SEMANTIC happens in the
 * API layer when the caller omits the {@code tier} argument. Checking it here would require
 * threading "was-this-defaulted" metadata through the entire call chain, which adds complexity
 * without enforcement value.</p>
 *
 * <p>This policy focuses on namespace enforcement because the engine level <em>does</em> know
 * whether the active namespace is the global default vs an explicitly configured one.</p>
 *
 * @since 1.6.0
 * @see MutationPolicy
 * @see WriteRequest
 */
public final class StrictWriteDestinationPolicy implements MutationPolicy {

    /** The engine's global default namespace ID. Writes to this namespace are rejected in strict mode. */
    private final String defaultNamespaceId;

    /**
     * Creates a strict write destination policy.
     *
     * @param defaultNamespaceId the engine's default namespace ID; writes targeting this
     *                           namespace are rejected as "unscoped"
     */
    public StrictWriteDestinationPolicy(final String defaultNamespaceId) {
        this.defaultNamespaceId = defaultNamespaceId != null ? defaultNamespaceId : "default";
    }

    @Override
    public void checkWrite(final WriteRequest request) {
        if (request.operationType() != WriteRequest.OperationType.REMEMBER) {
            return; // Only enforce on ingestion — forget/reinforce/consolidate operate on existing records
        }

        if (defaultNamespaceId.equals(request.namespaceId())) {
            throw new SpectorValidationException(
                    ErrorCode.DESTINATION_SCOPE_REQUIRED,
                    "namespace",
                    "caller must supply an explicit namespace — the default namespace '"
                            + defaultNamespaceId + "' is not permitted when strict write destination policy is active"
            );
        }
    }

    @Override
    public String toString() {
        return "StrictWriteDestinationPolicy{defaultNamespace='" + defaultNamespaceId + "'}";
    }
}
