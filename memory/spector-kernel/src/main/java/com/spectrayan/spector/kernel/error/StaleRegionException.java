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
package com.spectrayan.spector.kernel.error;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;

/**
 * Thrown by {@code HeaderCursor.seek} when a region grew or remapped concurrently,
 * invalidating the cursor's pinned generation (spec D8, R6.3).
 */
public class StaleRegionException extends SpectorStorageException {

    private final long pinnedGeneration;
    private final long currentGeneration;

    public StaleRegionException(String message) {
        super(ErrorCode.SEGMENT_CLOSED, message);
        this.pinnedGeneration = -1;
        this.currentGeneration = -1;
    }

    public StaleRegionException(long pinnedGeneration, long currentGeneration) {
        super(ErrorCode.SEGMENT_CLOSED,
                "Region generation stale: pinned=" + pinnedGeneration + ", current=" + currentGeneration);
        this.pinnedGeneration = pinnedGeneration;
        this.currentGeneration = currentGeneration;
    }

    public long pinnedGeneration() {
        return pinnedGeneration;
    }

    public long currentGeneration() {
        return currentGeneration;
    }
}
