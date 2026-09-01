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
