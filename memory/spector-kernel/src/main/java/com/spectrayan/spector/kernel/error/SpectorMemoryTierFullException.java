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
import com.spectrayan.spector.commons.error.SpectorMemoryException;

public class SpectorMemoryTierFullException extends SpectorMemoryException {

    private final String tier;
    private final int capacity;

    public SpectorMemoryTierFullException(String tier, int capacity) {
        super(ErrorCode.MEMORY_TIER_FULL, tier, capacity);
        this.tier = tier;
        this.capacity = capacity;
    }

    public SpectorMemoryTierFullException(String tier, int capacity, Throwable cause) {
        super(ErrorCode.MEMORY_TIER_FULL, cause, tier, capacity);
        this.tier = tier;
        this.capacity = capacity;
    }

    public String getTier() {
        return tier;
    }

    public int getCapacity() {
        return capacity;
    }
}
