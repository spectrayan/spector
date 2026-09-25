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
package com.spectrayan.spector.memory.policy;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;

import java.util.Objects;
import java.util.function.LongSupplier;

public class FencingMutationPolicy implements MutationPolicy {

    private final LongSupplier currentEpochSupplier;

    public FencingMutationPolicy(LongSupplier currentEpochSupplier) {
        this.currentEpochSupplier = Objects.requireNonNull(currentEpochSupplier, "currentEpochSupplier");
    }

    @Override
    public void checkWrite(WriteRequest request) {
        long currentEpoch = currentEpochSupplier.getAsLong();
        if (request.epoch() < currentEpoch) {
            throw new SpectorServerException(ErrorCode.FENCED, "Write rejected: request epoch " + request.epoch() + " is older than current epoch " + currentEpoch);
        }
    }
}
