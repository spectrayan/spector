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
package com.spectrayan.spector.synapse.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Diagnostic execution summary for a federated recall operation (ADR-0029 §7).
 *
 * @param queriedCount         number of namespaces evaluated
 * @param openedNamespaces     list of namespace IDs or slugs successfully opened and queried
 * @param skippedColdNamespaces list of cold namespace IDs or slugs skipped due to maxColdOpens budget
 * @param deniedNamespaces     list of namespace IDs or slugs where the caller lacks access
 * @param failedNamespaces     list of namespace IDs or slugs that failed during execution
 * @param executionDurationMs  total elapsed duration of the federated query in milliseconds
 */
public record FederatedRecallSummary(
        @JsonProperty("queriedCount") int queriedCount,
        @JsonProperty("openedNamespaces") List<String> openedNamespaces,
        @JsonProperty("skippedColdNamespaces") List<String> skippedColdNamespaces,
        @JsonProperty("deniedNamespaces") List<String> deniedNamespaces,
        @JsonProperty("failedNamespaces") List<String> failedNamespaces,
        @JsonProperty("executionDurationMs") long executionDurationMs
) {}
