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
package com.spectrayan.spector.synapse.agent.approval.repository;

import com.spectrayan.spector.synapse.agent.approval.model.AgentActionApproval;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence port for storing and querying {@link AgentActionApproval} entities.
 */
public interface AgentApprovalRepository {

    /**
     * Saves a new pending approval and registers its asynchronous completion future.
     */
    AgentActionApproval save(AgentActionApproval approval, CompletableFuture<AgentActionApproval> future);

    /**
     * Finds an approval entity by ID.
     */
    Optional<AgentActionApproval> findById(String id);

    /**
     * Finds the completion future associated with a pending approval.
     */
    Optional<CompletableFuture<AgentActionApproval>> findFutureById(String id);

    /**
     * Updates an existing approval entity state.
     */
    boolean update(AgentActionApproval approval);

    /**
     * Lists all approvals currently in PENDING status.
     */
    List<AgentActionApproval> findPending();

    /**
     * Lists all approvals (pending and historical) up to the specified limit.
     */
    List<AgentActionApproval> findAll(int limit);

    /**
     * Evicts resolved approval records older than the specified duration.
     */
    int deleteResolvedOlderThan(Duration maxAge);

    /**
     * Clears all stored approvals (primarily for testing).
     */
    void deleteAll();

    /**
     * Returns total count of stored records.
     */
    int count();
}
