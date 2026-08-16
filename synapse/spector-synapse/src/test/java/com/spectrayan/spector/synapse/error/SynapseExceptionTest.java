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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SynapseExceptionTest {

    @Test
    @DisplayName("SynapseNotFoundException sets ErrorCode and codeId")
    void notFoundException() {
        SynapseNotFoundException ex = new SynapseNotFoundException("User", "user-123");
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.API_NOT_FOUND);
        assertThat(ex.codeId()).isEqualTo(ErrorCode.API_NOT_FOUND.id());
        assertThat(ex.getMessage()).contains("User");
        assertThat(ex.getMessage()).contains("user-123");
    }

    @Test
    @DisplayName("SynapseConflictException sets ErrorCode and message")
    void conflictException() {
        SynapseConflictException ex = new SynapseConflictException("User already exists: admin");
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.API_CONFLICT);
        assertThat(ex.codeId()).isEqualTo(ErrorCode.API_CONFLICT.id());
        assertThat(ex.getMessage()).contains("admin");
    }

    @Test
    @DisplayName("SynapseDatabaseException wraps cause and exposes operation/entity")
    void databaseException() {
        RuntimeException cause = new RuntimeException("DB down");
        SynapseDatabaseException ex = new SynapseDatabaseException("insertUser", "users", cause);
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.DISK_IO_FAILED);
        assertThat(ex.codeId()).isEqualTo(ErrorCode.DISK_IO_FAILED.id());
        assertThat(ex.operation()).isEqualTo("insertUser");
        assertThat(ex.entityName()).isEqualTo("users");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("SynapseCacheException sets error code and cache name")
    void cacheException() {
        SynapseCacheException ex = new SynapseCacheException("user-accounts", "Failed to evict entry");
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.API_BAD_REQUEST);
        assertThat(ex.cacheName()).isEqualTo("user-accounts");
        assertThat(ex.getMessage()).contains("user-accounts");
    }
}
