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
package com.spectrayan.spector.memory.replication;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Thrown when a replica's sealed partition set diverges from the owner's sealed partition set
 * (e.g. checksum mismatch for an existing sealed partition ID), indicating corrupted or split history
 * that requires a full resync (Req R2.5, Task 2.5).
 */
public class SealedSetDivergenceException extends SpectorValidationException {

    private final String partitionId;
    private final String ownerSha256;
    private final String replicaSha256;

    public SealedSetDivergenceException(String partitionId, String ownerSha256, String replicaSha256) {
        super(ErrorCode.FILE_FORMAT_INVALID, String.format(
                "Sealed partition '%s' diverged: owner checksum %s != replica checksum %s. Full resync required.",
                partitionId, ownerSha256, replicaSha256));
        this.partitionId = partitionId;
        this.ownerSha256 = ownerSha256;
        this.replicaSha256 = replicaSha256;
    }

    public String partitionId() {
        return partitionId;
    }

    public String ownerSha256() {
        return ownerSha256;
    }

    public String replicaSha256() {
        return replicaSha256;
    }
}
