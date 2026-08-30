/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.catalog;

/**
 * Fine-grained actions for identity region and namespace grants. INJECT on a soul region is NOT equivalent to READ on traces.
 */
public enum GrantAction {

    /**
     * Read action allowing inspection or querying of the target object.
     */
    READ,

    /**
     * Write action allowing modifications or appends to the target object.
     */
    WRITE,

    /**
     * Administrative action allowing grant management and policy configuration.
     */
    ADMIN,

    /**
     * Injection action allowing prompt/context injection into agent execution without exposing raw data.
     */
    INJECT
}
