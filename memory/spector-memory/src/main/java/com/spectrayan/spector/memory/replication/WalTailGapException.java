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
 * Thrown when an unrecoverable gap is detected in a follower's WAL tail stream (Req R4.2, Task 3.7).
 *
 * <p>A gap forces full resync and must never be silently skipped.</p>
 */
public class WalTailGapException extends SpectorValidationException {

    private final long expectedSequence;
    private final long actualSequence;

    public WalTailGapException(long expectedSequence, long actualSequence) {
        super(ErrorCode.FILE_FORMAT_INVALID, String.format(
                "WAL tail gap detected: expected sequence %d but received %d. Forcing full resync (Req R4.2).",
                expectedSequence, actualSequence));
        this.expectedSequence = expectedSequence;
        this.actualSequence = actualSequence;
    }

    public long expectedSequence() {
        return expectedSequence;
    }

    public long actualSequence() {
        return actualSequence;
    }
}
