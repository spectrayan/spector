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
