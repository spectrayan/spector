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
package com.spectrayan.spector.synapse.error;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Thrown when an administrative cache operation (e.g. invalidation, inspection) encounters an error.
 */
public class SynapseCacheException extends SynapseException {

    private final String cacheName;

    public SynapseCacheException(String cacheName, String message) {
        super(ErrorCode.API_BAD_REQUEST, "Cache [" + cacheName + "]: " + message);
        this.cacheName = cacheName;
    }

    public SynapseCacheException(String cacheName, Throwable cause) {
        super(ErrorCode.API_BAD_REQUEST, cause, "Cache [" + cacheName + "]: " + cause.getMessage());
        this.cacheName = cacheName;
    }

    public String getCacheName() {
        return cacheName;
    }

    public String cacheName() {
        return cacheName;
    }
}
