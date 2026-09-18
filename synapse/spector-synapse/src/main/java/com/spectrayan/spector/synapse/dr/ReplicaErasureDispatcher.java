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
package com.spectrayan.spector.synapse.dr;

import java.util.List;

/**
 * Dispatcher interface for propagating namespace erasure to replica storage nodes (ADR-0034 §16, Req R6.3, G37).
 */
@FunctionalInterface
public interface ReplicaErasureDispatcher {

    record ReplicaErasureResult(
            int successfulAcks,
            int failedAcks,
            List<String> unreachableReplicas
    ) {
        public static ReplicaErasureResult success(int acks) {
            return new ReplicaErasureResult(acks, 0, List.of());
        }

        public static ReplicaErasureResult failed(int acks, List<String> unreachable) {
            return new ReplicaErasureResult(acks, unreachable != null ? unreachable.size() : 0, unreachable != null ? List.copyOf(unreachable) : List.of());
        }

        public boolean isComplete() {
            return failedAcks == 0 && unreachableReplicas.isEmpty();
        }
    }

    ReplicaErasureResult dispatchErasure(String namespaceId);
}
