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
package com.spectrayan.spector.memory.pathway.remember.relay;

import com.spectrayan.spector.commons.pathway.Specification;

/**
 * Named specification gates for conditional execution in the remember pathway.
 */
public final class RememberGates {

    private RememberGates() {}

    /**
     * Gate checking that the memory is not a duplicate before proceeding with heavy operations.
     */
    public static final Specification<RememberSignal> NOT_DUPLICATE = Specification.of(
            "Memory ID is already indexed (duplicate)",
            signal -> !signal.isDuplicate()
    );

    /**
     * Gate checking that the memory write transaction succeeded.
     */
    public static final Specification<RememberSignal> WRITE_SUCCESSFUL = Specification.of(
            "Cortical write transaction did not complete successfully",
            RememberSignal::isSuccessful
    );
}
