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
package com.spectrayan.spector.cluster.membership;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of active ring membership for a cell (ADR-0034 §15.2, Req R7.1–R7.3).
 *
 * @param cellId      the cell identifier
 * @param ringVersion monotonic generation of this membership set
 * @param members     immutable list of active member node identifiers
 */
public record CellMembership(String cellId, int ringVersion, List<String> members) {

    public CellMembership {
        if (cellId == null || cellId.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "cellId", "cellId must not be null or blank in CellMembership");
        }
        Objects.requireNonNull(members, "members list must not be null");
        members = List.copyOf(members);
    }
}
