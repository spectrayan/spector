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
 * Response returned from a federated recall operation (ADR-0029 §7).
 *
 * @param hits    ranked list of hits across queried rememberers
 * @param summary execution statistics and diagnostic summary
 */
public record FederatedRecallResponse(
        @JsonProperty("hits") List<FederatedRecallHit> hits,
        @JsonProperty("summary") FederatedRecallSummary summary
) {}
