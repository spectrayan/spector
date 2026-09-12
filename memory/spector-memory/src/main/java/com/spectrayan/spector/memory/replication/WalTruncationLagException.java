/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.replication;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Thrown when a follower requests WAL records that have already been truncated on the owner
 * (Req R4.4, Task 3.9).
 *
 * <p>Forces the lagging follower to perform a full snapshot resync rather than silently
 * stalling or reporting healthy with missing data.</p>
 */
public class WalTruncationLagException extends SpectorValidationException {

    private final long requestedSequence;
    private final long earliestAvailableSequence;

    public WalTruncationLagException(long requestedSequence, long earliestAvailableSequence) {
        super(ErrorCode.FILE_FORMAT_INVALID, String.format(
                "Follower requested WAL sequence %d, but earliest available sequence is %d (truncated). Forcing full resync (Req R4.4).",
                requestedSequence, earliestAvailableSequence));
        this.requestedSequence = requestedSequence;
        this.earliestAvailableSequence = earliestAvailableSequence;
    }

    public long requestedSequence() {
        return requestedSequence;
    }

    public long earliestAvailableSequence() {
        return earliestAvailableSequence;
    }
}
