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
package com.spectrayan.spector.synapse.catalog.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;

@DisplayName("AccountDefaultController REST API Tests")
class AccountDefaultControllerTest {

    private AccountCatalog catalog;
    private AccountDefaultController controller;

    private static final String TEST_ACCOUNT = "0195500000001";

    @BeforeEach
    void setUp() {
        catalog = mock(AccountCatalog.class);
        controller = new AccountDefaultController(catalog);

        var auth = new UsernamePasswordAuthenticationToken(TEST_ACCOUNT, "pw", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("PUT /api/v1/account/default-namespace sets default namespace successfully")
    void testSetDefaultNamespace() {
        var record = new NamespaceRecord(
                "0195500000002", "project-x", TEST_ACCOUNT, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Project X", null, null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(TEST_ACCOUNT, "project-x")).thenReturn(Optional.of(record));

        var request = new SetDefaultNamespaceRequest("project-x");
        ResponseEntity<Map<String, String>> response = controller.setDefaultNamespace(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "updated");
        assertThat(response.getBody()).containsEntry("defaultNamespaceId", "0195500000002");
        verify(catalog).setDefaultNamespace(TEST_ACCOUNT, "0195500000002");
    }

    @Test
    @DisplayName("PUT /api/v1/account/default-namespace throws NamespaceNotFoundException when target does not exist")
    void testSetDefaultNamespaceNotFound() {
        when(catalog.resolve(TEST_ACCOUNT, "nonexistent")).thenReturn(Optional.empty());

        var request = new SetDefaultNamespaceRequest("nonexistent");
        assertThatThrownBy(() -> controller.setDefaultNamespace(request))
                .isInstanceOf(NamespaceNotFoundException.class);
    }

    @Test
    @DisplayName("PUT /api/v1/account/default-namespace rejects blank namespace")
    void testSetDefaultNamespaceBlank() {
        var request = new SetDefaultNamespaceRequest("   ");
        assertThatThrownBy(() -> controller.setDefaultNamespace(request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
