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
 * Type of object targeted by a grant.
 */
public enum GrantObjectType {

    /**
     * Namespace resource grant target.
     */
    NAMESPACE,

    /**
     * Identity bundle grant target containing cohesive identity configuration and soul states.
     */
    IDENTITY_BUNDLE,

    /**
     * Identity region grant target representing a specific section of an agent soul.
     */
    IDENTITY_REGION
}
