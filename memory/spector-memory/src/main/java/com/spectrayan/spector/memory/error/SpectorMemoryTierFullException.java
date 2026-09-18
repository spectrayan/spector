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
package com.spectrayan.spector.memory.error;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;

/**
 * Exception thrown when a cognitive memory tier has reached its capacity limits.
 *
 * @deprecated Use {@link com.spectrayan.spector.kernel.error.SpectorMemoryTierFullException} directly.
 */
@Deprecated(since = "0.1.0-beta", forRemoval = true)
public class SpectorMemoryTierFullException extends com.spectrayan.spector.kernel.error.SpectorMemoryTierFullException {

    public SpectorMemoryTierFullException(String tier, int capacity) {
        super(tier, capacity);
    }

    public SpectorMemoryTierFullException(String tier, int capacity, Throwable cause) {
        super(tier, capacity, cause);
    }
}
