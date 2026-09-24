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

/**
 * A policy the engine consults before mutating or destroying data, and cannot itself evaluate.
 *
 * <h2>Why the engine asks instead of deciding</h2>
 * <p>Some reasons to refuse a mutation live entirely outside the engine. Legal hold is recorded in the
 * account catalog; fence currency is decided by the cell coordinator. The engine has no access to either and
 * must not grow one — it would drag a control-plane dependency into a library that is meant to run
 * embedded. So it asks, and a host that knows the answer supplies it.</p>
 *
 * <h2>Consulted from the engine, not from the API layer</h2>
 * <p>The check belongs here rather than in a REST controller because REST is one of several ways in. MCP
 * tools, the embedded Java API, scheduled consolidation and bulk chat-memory paths all reach the same
 * deletion code. A check placed at the HTTP boundary is not an enforcement mechanism; it is a check on one
 * caller, and every other caller walks past it.</p>
 *
 * <h2>Fail closed</h2>
 * <p>Implementations MUST throw rather than return false when they cannot establish that an operation is
 * permitted — including when the namespace cannot be resolved at all. An unresolvable namespace is not an
 * absent hold; it is an unknown one, and treating unknown as permitted is how held data gets destroyed.
 * {@code TenantErasureService} already takes this position for namespace erasure and this SPI keeps it.</p>
 *
 * <h2>Default is no-op</h2>
 * <p>Both methods default to permitting everything, so embedded and OSS deployments take no new dependency
 * and see no behaviour change. {@link #ALLOW_ALL} is the engine's default. This is deliberate: the policy is
 * an enterprise concern, and an engine that refused to start without one would be imposing it on users who
 * have no catalog to consult.</p>
 *
 * <h2>Called on the caller's thread, inside the operation</h2>
 * <p>Implementations should be fast and side-effect free. They run before the mutation, on the calling
 * thread, while engine locks may be held — so they must not call back into the engine, which would deadlock.
 * Throwing is the only supported way to refuse.</p>
 *
 * @see DeletionRequest
 * @see WriteRequest
 */
public interface MutationPolicy {

    /**
     * Approves or refuses a deletion.
     *
     * <p>Refuse by throwing. The exception propagates to the caller unchanged, so implementations should
     * throw the type operators already recognise — {@code NamespaceLegalHoldException} for a hold — rather
     * than inventing a new one per call site.</p>
     *
     * @param request what the engine is about to delete
     * @throws RuntimeException to refuse the deletion
     */
    default void checkDeletion(DeletionRequest request) {
        // Permit by default.
    }

    /**
     * Approves or refuses a state mutation.
     *
     * <p>Refuse by throwing.</p>
     *
     * <p>Currently unused by the engine: this is the seam {@code cell-failover-fencing} Phase 4 will use for
     * fence validation on the write path. It is declared now so that work plugs into an existing interface
     * rather than reshaping this one or adding a second interception layer — which is how a codebase ends up
     * with two mechanisms for "consult a policy you cannot see".</p>
     *
     * @param request what the engine is about to write
     * @throws RuntimeException to refuse the write
     */
    default void checkWrite(WriteRequest request) {
        // Permit by default.
    }

    /**
     * The permissive default: allows every mutation and deletion.
     *
     * <p>Used when no host policy is installed, which is the normal case for embedded and OSS use.</p>
     */
    MutationPolicy ALLOW_ALL = new MutationPolicy() {
        @Override
        public String toString() {
            return "MutationPolicy.ALLOW_ALL";
        }
    };
}
