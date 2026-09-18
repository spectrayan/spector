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
 * Response returned from a federated recall operation (ADR-0029 §7).
 *
 * @param hits    ranked list of hits across queried rememberers
 * @param summary execution statistics and diagnostic summary
 */
public record FederatedRecallResponse(
        @JsonProperty("hits") List<FederatedRecallHit> hits,
        @JsonProperty("summary") FederatedRecallSummary summary
) {}
