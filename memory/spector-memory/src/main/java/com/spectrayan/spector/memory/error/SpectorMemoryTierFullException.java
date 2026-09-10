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
