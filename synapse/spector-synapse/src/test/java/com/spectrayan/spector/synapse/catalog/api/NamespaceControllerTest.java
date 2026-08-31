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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

@DisplayName("NamespaceController REST API Tests")
class NamespaceControllerTest {

    private AccountCatalog catalog;
    private NamespaceController controller;

    private static final String TEST_ACCOUNT = "0195500000001";

    @BeforeEach
    void setUp() {
        catalog = mock(AccountCatalog.class);
        controller = new NamespaceController(catalog);

        var auth = new UsernamePasswordAuthenticationToken(TEST_ACCOUNT, "pw", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/namespaces returns accessible namespaces")
    void testListNamespaces() {
        var record = new NamespaceRecord(
                TEST_ACCOUNT, "default", TEST_ACCOUNT, NamespaceType.DEFAULT,
                NamespaceStatus.ACTIVE, "Default", "Autobiographical", null, Instant.now(), Instant.now()
        );
        when(catalog.listAccessible(TEST_ACCOUNT)).thenReturn(List.of(record));

        ResponseEntity<List<NamespaceResponse>> response = controller.listNamespaces();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).slug()).isEqualTo("default");
        assertThat(response.getBody().get(0).namespaceId()).isEqualTo(TEST_ACCOUNT);
    }

    @Test
    @DisplayName("POST /api/v1/namespaces creates namespace and returns 201 CREATED")
    void testCreateNamespace() {
        var record = new NamespaceRecord(
                "0195500000002", "project-x", TEST_ACCOUNT, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Project X", "Test Project", null, Instant.now(), Instant.now()
        );
        when(catalog.createNamespace(eq(TEST_ACCOUNT), eq("project-x"), eq(NamespaceType.PROJECT),
                eq("Project X"), eq("Test Project"), any())).thenReturn(record);

        var request = new CreateNamespaceRequest("project-x", NamespaceType.PROJECT, "Project X", "Test Project", null);
        ResponseEntity<NamespaceResponse> response = controller.createNamespace(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().slug()).isEqualTo("project-x");
        assertThat(response.getBody().namespaceId()).isEqualTo("0195500000002");
    }

    @Test
    @DisplayName("GET /api/v1/namespaces/{slugOrId} returns namespace when found")
    void testGetNamespaceFound() {
        var record = new NamespaceRecord(
                "0195500000002", "project-x", TEST_ACCOUNT, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Project X", null, null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(TEST_ACCOUNT, "project-x")).thenReturn(Optional.of(record));

        ResponseEntity<NamespaceResponse> response = controller.getNamespace("project-x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().slug()).isEqualTo("project-x");
    }

    @Test
    @DisplayName("GET /api/v1/namespaces/{slugOrId} throws NamespaceNotFoundException when missing")
    void testGetNamespaceNotFound() {
        when(catalog.resolve(TEST_ACCOUNT, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getNamespace("missing"))
                .isInstanceOf(NamespaceNotFoundException.class);
    }

    @Test
    @DisplayName("PUT /api/v1/namespaces/{slugOrId} updates metadata and returns 200 OK")
    void testUpdateNamespace() {
        var updated = new NamespaceRecord(
                "0195500000002", "project-x", TEST_ACCOUNT, NamespaceType.AGENT,
                NamespaceStatus.ACTIVE, "Updated Name", "Updated Desc", null, Instant.now(), Instant.now()
        );
        when(catalog.updateNamespace(eq(TEST_ACCOUNT), eq("project-x"), eq("Updated Name"), eq("Updated Desc"),
                eq(NamespaceType.AGENT), any())).thenReturn(updated);

        var request = new UpdateNamespaceRequest("Updated Name", "Updated Desc", NamespaceType.AGENT, null);
        ResponseEntity<NamespaceResponse> response = controller.updateNamespace("project-x", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().displayName()).isEqualTo("Updated Name");
        assertThat(response.getBody().type()).isEqualTo(NamespaceType.AGENT);
    }

    @Test
    @DisplayName("DELETE /api/v1/namespaces/{slugOrId} deletes namespace and returns 204 NO_CONTENT")
    void testDeleteNamespace() {
        ResponseEntity<Void> response = controller.deleteNamespace("project-x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(catalog).tombstone(TEST_ACCOUNT, "project-x");
    }

    @Test
    @DisplayName("POST /api/v1/namespaces/{slugOrId}/reset resets namespace and returns 200 OK")
    void testResetNamespace() {
        ResponseEntity<Map<String, String>> response = controller.resetNamespace("default");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "reset");
        verify(catalog).resetNamespace(TEST_ACCOUNT, "default");
    }

    @Test
    @DisplayName("GET /api/v1/namespaces/{slugOrId}/grants returns active grants")
    void testListGrants() {
        var grant = new com.spectrayan.spector.synapse.catalog.Grant(
                "grant-1",
                com.spectrayan.spector.synapse.catalog.GrantObjectType.NAMESPACE,
                "0195500000002",
                "0195500000099",
                com.spectrayan.spector.synapse.catalog.PrincipalType.ACCOUNT,
                com.spectrayan.spector.synapse.catalog.GrantRole.READER,
                null,
                TEST_ACCOUNT,
                Instant.now(),
                null,
                null
        );
        when(catalog.listGrants(TEST_ACCOUNT, "project-x")).thenReturn(List.of(grant));

        ResponseEntity<List<GrantResponse>> response = controller.listGrants("project-x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).grantId()).isEqualTo("grant-1");
        assertThat(response.getBody().get(0).role()).isEqualTo(com.spectrayan.spector.synapse.catalog.GrantRole.READER);
    }

    @Test
    @DisplayName("POST /api/v1/namespaces/{slugOrId}/grants creates grant and returns 201 CREATED")
    void testCreateGrant() {
        var grant = new com.spectrayan.spector.synapse.catalog.Grant(
                "grant-2",
                com.spectrayan.spector.synapse.catalog.GrantObjectType.NAMESPACE,
                "0195500000002",
                "0195500000099",
                com.spectrayan.spector.synapse.catalog.PrincipalType.ACCOUNT,
                com.spectrayan.spector.synapse.catalog.GrantRole.WRITER,
                null,
                TEST_ACCOUNT,
                Instant.now(),
                null,
                null
        );
        when(catalog.grantNamespace(eq(TEST_ACCOUNT), eq("project-x"), eq("0195500000099"),
                eq(com.spectrayan.spector.synapse.catalog.GrantRole.WRITER), any(), any())).thenReturn(grant);

        var request = new CreateGrantRequest("0195500000099", com.spectrayan.spector.synapse.catalog.GrantRole.WRITER, null, null);
        ResponseEntity<GrantResponse> response = controller.createGrant("project-x", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().grantId()).isEqualTo("grant-2");
        assertThat(response.getBody().granteeAccountId()).isEqualTo("0195500000099");
    }

    @Test
    @DisplayName("DELETE /api/v1/namespaces/{slugOrId}/grants/{grantId} revokes grant and returns 204 NO_CONTENT")
    void testRevokeGrant() {
        ResponseEntity<Void> response = controller.revokeGrant("project-x", "grant-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(catalog).revokeNamespaceGrant(TEST_ACCOUNT, "project-x", "grant-1");
    }
}
