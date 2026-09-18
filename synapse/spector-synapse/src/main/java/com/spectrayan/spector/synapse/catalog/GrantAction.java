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
