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
 * Lifecycle status of a namespace in the catalog.
 */
public enum NamespaceStatus {

    /**
     * Active namespace available for standard read and write operations.
     */
    ACTIVE,

    /**
     * Tombstoned namespace marked for asynchronous garbage collection and cleanup.
     */
    TOMBSTONED,

    /**
     * Archived namespace preserved in read-only mode.
     */
    ARCHIVED,

    /**
     * Namespace under legal hold preventing modification, archiving, or deletion.
     */
    LEGAL_HOLD
}
