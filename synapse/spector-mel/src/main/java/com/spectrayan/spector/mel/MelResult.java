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
package com.spectrayan.spector.mel;

import java.util.Optional;

/**
 * Result of evaluating a MEL statement against a memory engine.
 *
 * @param status     OK or ERROR
 * @param output     human-readable formatted output
 * @param elapsedMs  execution time in milliseconds
 * @param error      error message if status is ERROR
 */
public record MelResult(
        Status status,
        String output,
        long elapsedMs,
        Optional<String> error
) {
    public enum Status { OK, ERROR }

    /** Creates a successful result. */
    public static MelResult ok(String output, long elapsedMs) {
        return new MelResult(Status.OK, output, elapsedMs, Optional.empty());
    }

    /** Creates an error result. */
    public static MelResult error(String message, long elapsedMs) {
        return new MelResult(Status.ERROR, "", elapsedMs, Optional.of(message));
    }

    /** Returns true if this result is successful. */
    public boolean isOk() {
        return status == Status.OK;
    }
}
