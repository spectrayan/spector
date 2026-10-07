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
package com.spectrayan.spector.synapse.security;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;

import com.spectrayan.spector.commons.security.SpectorRoles;
import com.spectrayan.spector.commons.security.SpectorScopes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpectorAuthorityMapper Unit Tests")
class SpectorAuthorityMapperTest {

    @Test
    @DisplayName("Empty or null inputs yield empty authorities")
    void testEmptyAndNullInputs() {
        assertThat(SpectorAuthorityMapper.toAuthorities(null, null)).isEmpty();
        assertThat(SpectorAuthorityMapper.toAuthorities(List.of(), List.of())).isEmpty();
        assertThat(SpectorAuthorityMapper.toAuthorities(List.of("", "   "), List.of("  "))).isEmpty();
        assertThat(SpectorAuthorityMapper.forRole(null)).isEmpty();
        assertThat(SpectorAuthorityMapper.forRole("  ")).isEmpty();
        assertThat(SpectorAuthorityMapper.forScopes(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "ADMIN", "Admin"})
    @DisplayName("Admin role in any case variant produces both ROLE_admin and ROLE_ADMIN")
    void testAdminRoleVariants(String roleName) {
        List<String> authorities = SpectorAuthorityMapper.forRole(roleName).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains("ROLE_admin", "ROLE_ADMIN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"super-admin", "SUPER_ADMIN", "super_admin", "SUPER-ADMIN", "SuperAdmin", "superAdmin"})
    @DisplayName("Super-admin in kebab, snake, screaming snake, and camel/PascalCase produces all required authorities")
    void testSuperAdminVariants(String roleName) {
        List<String> authorities = SpectorAuthorityMapper.forRole(roleName).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains(
                "ROLE_super-admin",
                "ROLE_SUPER_ADMIN",
                "ROLE_super_admin",
                "ROLE_SUPER-ADMIN"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"viewer", "VIEWER", "Viewer"})
    @DisplayName("Viewer role variants produce both lowercase and uppercase authorities")
    void testViewerVariants(String roleName) {
        List<String> authorities = SpectorAuthorityMapper.forRole(roleName).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains("ROLE_viewer", "ROLE_VIEWER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"agent", "AGENT", "Agent"})
    @DisplayName("Agent role variants produce both lowercase and uppercase authorities")
    void testAgentVariants(String roleName) {
        List<String> authorities = SpectorAuthorityMapper.forRole(roleName).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains("ROLE_agent", "ROLE_AGENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"editor", "EDITOR", "Editor"})
    @DisplayName("Editor role variants produce both lowercase and uppercase authorities")
    void testEditorVariants(String roleName) {
        List<String> authorities = SpectorAuthorityMapper.forRole(roleName).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains("ROLE_editor", "ROLE_EDITOR");
    }

    @Test
    @DisplayName("forRoleWithScopes maps constituent scopes for SpectorRoles")
    void testForRoleWithScopes() {
        List<String> viewerAuthorities = SpectorAuthorityMapper.forRoleWithScopes("viewer").stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(viewerAuthorities).contains(
                "ROLE_viewer",
                "ROLE_VIEWER",
                "SCOPE_" + SpectorScopes.MEMORY_READ,
                "SCOPE_" + SpectorScopes.SEARCH_READ
        );

        List<String> superAdminAuthorities = SpectorAuthorityMapper.forRoleWithScopes("SuperAdmin").stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(superAdminAuthorities).contains(
                "ROLE_super-admin",
                "ROLE_SUPER_ADMIN",
                "SCOPE_" + SpectorScopes.ADMIN,
                "SCOPE_" + SpectorScopes.MEMORY_READ,
                "SCOPE_" + SpectorScopes.NAMESPACE_ADMIN
        );
    }

    @Test
    @DisplayName("Scopes preserve raw and stripped prefixes")
    void testScopesPreservePrefixes() {
        List<String> authorities = SpectorAuthorityMapper.forScopes(List.of("spector:memory:read", "SCOPE_memory:write")).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains(
                "SCOPE_spector:memory:read",
                "SCOPE_memory:write"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"role_admin", "ROLE_admin", "Role_admin", "role-admin", " ROLE_admin "})
    @DisplayName("Admin role with prefixed or whitespace input normalizes correctly")
    void testPrefixedAdminVariants(String roleName) {
        List<String> authorities = SpectorAuthorityMapper.forRole(roleName).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains("ROLE_admin", "ROLE_ADMIN");
        assertThat(authorities).doesNotContain("ROLE_role_admin", "ROLE_ROLE_admin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"role_super-admin", "ROLE_super-admin", "role-super-admin", " ROLE_SUPER_ADMIN "})
    @DisplayName("Super-admin role with prefixed or whitespace input normalizes correctly")
    void testPrefixedSuperAdminVariants(String roleName) {
        List<String> authorities = SpectorAuthorityMapper.forRole(roleName).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains("ROLE_super-admin", "ROLE_SUPER_ADMIN");
        assertThat(authorities).doesNotContain("ROLE_role_super-admin", "ROLE_ROLE_super-admin");
    }

    @Test
    @DisplayName("forScopes handles lowercase scope_ and scope- prefixes and whitespace")
    void testForScopesWithVariedPrefixes() {
        List<String> authorities = SpectorAuthorityMapper.forScopes(List.of("scope_memory:read", "scope-memory:write", " spector:search:read ")).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains(
                "SCOPE_memory:read",
                "SCOPE_memory:write",
                "SCOPE_spector:search:read"
        );
        assertThat(authorities).doesNotContain("SCOPE_scope_memory:read", "SCOPE_scope-memory:write");
    }
}
