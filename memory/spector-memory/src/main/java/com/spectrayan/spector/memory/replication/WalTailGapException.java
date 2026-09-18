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
