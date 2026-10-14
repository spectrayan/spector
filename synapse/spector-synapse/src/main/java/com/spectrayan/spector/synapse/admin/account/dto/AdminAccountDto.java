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
package com.spectrayan.spector.synapse.admin.account.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;

/**
 * Content-free request and response DTOs for the Admin Account and Agent-Principal Registry API
 * at {@code /api/v1/admin/accounts} (ADR-0087, Issue #1056).
 *
 * <p><strong>Privacy Contract</strong>: Under ADR-0087, no DTO in this class may expose
 * memory text, vector embeddings, tags, labels, raw secrets, or user-supplied memory metadata.</p>
 */
public final class AdminAccountDto {

    private AdminAccountDto() {}

    /**
     * Per-account namespace and hot-namespace quota settings.
     *
     * @param maxNamespaces    maximum owned namespaces permitted for this account (nullable for profile default)
     * @param maxHotNamespaces maximum hot namespaces permitted concurrently for this account (nullable for profile default)
     */
    public record AccountQuotaSummaryDto(
            Integer maxNamespaces,
            Integer maxHotNamespaces
    ) {}

    /**
     * Content-free summary of an agent or service principal created/owned by an account.
     *
     * @param accountId   13-character TSID of the agent/service principal
     * @param username    unique handle of the principal
     * @param displayName human-readable name
     * @param kind        principal kind ({@code AGENT} or {@code SERVICE})
     * @param profile     packaging profile
     * @param active      whether the principal is active
     * @param createdAt   creation timestamp
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OwnedAgentSummaryDto(
            String accountId,
            String username,
            String displayName,
            PrincipalKind kind,
            AccountProfile profile,
            boolean active,
            Instant createdAt
    ) {}

    /**
     * Content-free summary of a namespace grant held by an account or agent principal.
     *
     * @param grantId              unique grant TSID
     * @param namespaceId          target namespace TSID
     * @param namespaceSlug        target namespace slug
     * @param namespaceDisplayName target namespace display name
     * @param namespaceOwnerId     owning account TSID of the namespace
     * @param role                 granted role ({@code OWNER}, {@code EDITOR}, {@code VIEWER}, {@code AGENT})
     * @param actions              granted actions list
     * @param grantedBy            account TSID that issued the grant
     * @param grantedAt            timestamp when the grant was issued
     * @param expiresAt            optional expiration timestamp
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AccountNamespaceGrantDto(
            String grantId,
            String namespaceId,
            String namespaceSlug,
            String namespaceDisplayName,
            String namespaceOwnerId,
            String role,
            List<String> actions,
            String grantedBy,
            Instant grantedAt,
            Instant expiresAt
    ) {}

    /**
     * Content-free representation of an account or agent principal combining identity metadata,
     * owner linkage, namespace grants, owned agent principals, and aggregate resource telemetry.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AccountAdminResponse(
            String accountId,
            String username,
            String displayName,
            String email,
            String tenantId,
            PrincipalKind kind,
            AccountProfile profile,
            Set<String> roles,
            Set<String> scopes,
            boolean active,
            boolean locked,
            boolean mustChangePassword,
            boolean legalHold,
            String defaultNamespaceId,
            String ownerAccountId,
            String ownerUsername,
            String ownerDisplayName,
            AccountQuotaSummaryDto quotas,
            long namespaceCount,
            long storageBytes,
            long apiKeyCount,
            Instant lastActivityAt,
            List<OwnedAgentSummaryDto> ownedAgents,
            List<AccountNamespaceGrantDto> namespaceGrants,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * Initial namespace grant specification when provisioning an agent or service principal.
     *
     * @param namespaceId target namespace TSID or slug
     * @param role        grant role (e.g. {@code AGENT}, {@code EDITOR}, {@code VIEWER})
     * @param expiresAt   optional grant expiration instant
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InitialNamespaceGrantDto(
            String namespaceId,
            String role,
            Instant expiresAt
    ) {}

    /**
     * Request payload for creating a human, agent, or service account via {@code POST /api/v1/admin/accounts}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateAccountRequest(
            String username,
            String password,
            String displayName,
            String email,
            String tenantId,
            PrincipalKind kind,
            AccountProfile profile,
            Set<String> roles,
            Set<String> scopes,
            Boolean mustChangePassword,
            Boolean legalHold,
            String ownerAccountId,
            Integer maxNamespaces,
            Integer maxHotNamespaces,
            List<InitialNamespaceGrantDto> initialGrants
    ) {}

    /**
     * Request payload for updating an account via {@code PUT /api/v1/admin/accounts/{accountId}}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateAccountRequest(
            String displayName,
            String email,
            Boolean active,
            AccountProfile profile,
            Set<String> roles,
            Set<String> scopes,
            Boolean legalHold,
            String ownerAccountId,
            Integer maxNamespaces,
            Integer maxHotNamespaces
    ) {}

    /**
     * Request payload for reassigning an account's tenant via {@code POST /api/v1/admin/accounts/{accountId}/tenant}
     * ({@code super-admin} only).
     */
    public record ReassignTenantRequest(
            String tenantId
    ) {}

    /**
     * Request payload for resetting an account's credentials via
     * {@code POST /api/v1/admin/accounts/{accountId}/reset-credentials}.
     *
     * @param newPassword         optional new password to set for the account
     * @param mustChangePassword  whether the user must change password on next login (defaults to true when password is reset)
     * @param revokeRefreshTokens whether to revoke all active refresh tokens (defaults to true)
     * @param revokeApiKeys       whether to revoke all inbound AUTH API keys owned by this account (defaults to false)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResetCredentialsRequest(
            String newPassword,
            Boolean mustChangePassword,
            Boolean revokeRefreshTokens,
            Boolean revokeApiKeys
    ) {}

    /**
     * Content-free response summarizing the actions performed during a credential reset.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResetCredentialsResponse(
            String accountId,
            boolean passwordReset,
            boolean refreshTokensRevoked,
            int revokedApiKeyCount,
            Instant resetAt
    ) {}
}
