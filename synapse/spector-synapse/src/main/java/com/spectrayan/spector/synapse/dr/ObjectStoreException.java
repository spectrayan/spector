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
package com.spectrayan.spector.synapse.dr;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;

/**
 * Thrown when an object-store communication or protocol error occurs during disaster recovery operations
 * (ADR-0034 §11.2, §14, Req R1.1, R1.6).
 */
public class ObjectStoreException extends SpectorException {

    private final String bucket;
    private final String key;

    public ObjectStoreException(String message) {
        super(ErrorCode.INTERNAL_ERROR, message);
        this.bucket = null;
        this.key = null;
    }

    public ObjectStoreException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, cause);
        this.bucket = null;
        this.key = null;
    }

    public ObjectStoreException(String bucket, String key, String message) {
        super(ErrorCode.INTERNAL_ERROR, String.format("ObjectStore error on bucket='%s', key='%s': %s", bucket, key, message));
        this.bucket = bucket;
        this.key = key;
    }

    public ObjectStoreException(String bucket, String key, String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, String.format("ObjectStore error on bucket='%s', key='%s': %s", bucket, key, message), cause);
        this.bucket = bucket;
        this.key = key;
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }
}
