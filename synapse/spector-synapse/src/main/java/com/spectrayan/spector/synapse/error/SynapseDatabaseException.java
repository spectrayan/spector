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
 * Thrown when a database or data access operation fails in Synapse.
 *
 * <p>Safely wraps low-level JDBC / Spring {@code DataAccessException} failures, ensuring
 * that raw SQL parameters, credentials, or internal query fragments are sanitized and not
 * leaked in the public error message.</p>
 */
public class SynapseDatabaseException extends SynapseException {

    private final String operation;
    private final String entityName;

    /**
     * Creates a new database exception.
     *
     * @param operation  the logical operation attempted (e.g. "saveCredential", "findByUsername")
     * @param entityName the target table or entity name (e.g. "credentials", "users")
     * @param cause      the underlying root cause (e.g. DataAccessException)
     */
    public SynapseDatabaseException(String operation, String entityName, Throwable cause) {
        super(ErrorCode.DISK_IO_FAILED, cause, entityName + " (" + operation + ")");
        this.operation = operation;
        this.entityName = entityName;
    }

    /**
     * Creates a new database exception with an explicit error code.
     *
     * @param errorCode  the Spector error code
     * @param operation  the logical operation attempted
     * @param entityName the target table or entity name
     * @param cause      the underlying root cause
     */
    public SynapseDatabaseException(ErrorCode errorCode, String operation, String entityName, Throwable cause) {
        super(errorCode, cause, entityName + " (" + operation + ")");
        this.operation = operation;
        this.entityName = entityName;
    }

    public String getOperation() {
        return operation;
    }

    public String getEntityName() {
        return entityName;
    }

    public String operation() {
        return operation;
    }

    public String entityName() {
        return entityName;
    }
}
