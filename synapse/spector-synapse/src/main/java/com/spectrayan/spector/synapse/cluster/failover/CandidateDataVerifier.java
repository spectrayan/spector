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
package com.spectrayan.spector.synapse.cluster.failover;

/**
 * Functional interface for verifying candidate replica data before promotion (Req R4.4, Q6).
 *
 * <p>Phase 3 verifies replication data on apply; the failover orchestrator verifies data again
 * before promotion because promotion is the critical moment where a replication defect becomes
 * data loss.</p>
 */
@FunctionalInterface
public interface CandidateDataVerifier {

    /**
     * Verifies that the candidate survivor node has valid and consistent data for the given namespace.
     *
     * @param namespaceId namespace being considered for failover promotion
     * @param candidateNodeId candidate survivor node identifier
     * @return {@code true} if data is verified and safe for promotion; {@code false} if verification fails
     */
    boolean verifyCandidateData(String namespaceId, String candidateNodeId);
}
