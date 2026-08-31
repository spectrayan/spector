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
package com.spectrayan.spector.synapse.memory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceTombstonedException;
import com.spectrayan.spector.synapse.catalog.exception.TokenNamespaceLockedException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.identity.IdentityPlane;

/**
 * Shared binder that resolves an authenticated request to a {@link MemoryBinding}
 * holding the target {@link SpectorMemory}, lease, and security context (ADR-0029 §16).
 *
 * <p>Both REST filters and MCP sessions delegate resolution to this component,
 * ensuring consistent resolution order:
 * {@code client signal (header/arg) > session default > account default}.</p>
 */
@Component
public class MemoryRequestBinder {

    private static final Logger log = LoggerFactory.getLogger(MemoryRequestBinder.class);
    private static final String DEFAULT_USER_ID = "default";

    private final AccountCatalog catalog;
    private final MemoryRegistry registry;
    private final SynapseProperties synapseProps;
    private final SpectorMemory sharedMemory;
    private final IdentityPlane identityPlane;

    public MemoryRequestBinder(
            AccountCatalog catalog,
            MemoryRegistry registry,
            SynapseProperties synapseProps,
            ObjectProvider<SpectorMemory> sharedMemoryProvider,
            ObjectProvider<IdentityPlane> identityPlaneProvider) {
        this.catalog = catalog;
        this.registry = registry;
        this.synapseProps = synapseProps;
        this.sharedMemory = sharedMemoryProvider != null ? sharedMemoryProvider.getIfAvailable() : null;
        this.identityPlane = identityPlaneProvider != null ? identityPlaneProvider.getIfAvailable() : null;
    }

    /**
     * Binds a memory instance for the given authentication and optional namespace selector.
     *
     * @param auth     the current authentication, or null if unauthenticated
     * @param selector optional namespace slug or namespaceId
     * @return the bound memory context
     */
    public MemoryBinding bind(Authentication auth, Optional<String> selector) {
        return bind(auth, selector, null);
    }

    /**
     * Binds a memory instance with optional session identifier.
     *
     * @param auth      the current authentication
     * @param selector  optional namespace slug or namespaceId
     * @param sessionId optional MCP connection or session identifier
     * @return the bound memory context
     */
    public MemoryBinding bind(Authentication auth, Optional<String> selector, String sessionId) {
        if (selector != null && selector.isPresent() && !selector.get().isBlank()) {
            String slugOrId = selector.get().trim();
            if ("*".equals(slugOrId) || slugOrId.contains("*") || slugOrId.contains(",")) {
                throw new com.spectrayan.spector.commons.error.SpectorValidationException(
                        com.spectrayan.spector.commons.error.ErrorCode.ARGUMENT_INVALID,
                        "Wildcard '*' or multi-namespace selection is not permitted on single-namespace memory operations. Use memory_federated_recall.");
            }
        }

        if (!synapseProps.auth().enabled() || auth == null || !auth.isAuthenticated()
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            AutoCloseable lease = sharedMemory != null ? sharedMemory.acquireLease() : null;
            RequestMemoryContext reqCtx = new RequestMemoryContext(
                    null, List.of(), DEFAULT_USER_ID, DEFAULT_USER_ID, "default",
                    GrantRole.OWNER, Set.of(), sessionId, List.of(), null);
            return new MemoryBinding(sharedMemory, DEFAULT_USER_ID, DEFAULT_USER_ID, "default", lease, reqCtx);
        }

        String accountId = auth.getName();
        if (accountId == null || accountId.isBlank() || DEFAULT_USER_ID.equals(accountId)) {
            AutoCloseable lease = sharedMemory != null ? sharedMemory.acquireLease() : null;
            RequestMemoryContext reqCtx = new RequestMemoryContext(
                    null, List.of(), DEFAULT_USER_ID, DEFAULT_USER_ID, "default",
                    GrantRole.OWNER, Set.of(), sessionId, List.of(), null);
            return new MemoryBinding(sharedMemory, DEFAULT_USER_ID, DEFAULT_USER_ID, "default", lease, reqCtx);
        }

        // Extract JWT claims
        TokenClaims tokenClaims = extractTokenClaims(auth);

        Account account = catalog.getOrCreateAccount(accountId);

        String targetSlug;
        String targetNamespaceId;
        NamespaceRecord record = null;

        if (selector != null && selector.isPresent() && !selector.get().isBlank()) {
            String slugOrId = selector.get().trim();
            record = catalog.resolve(accountId, slugOrId)
                    .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));
            if (record.status() == NamespaceStatus.TOMBSTONED) {
                throw new NamespaceTombstonedException(record.namespaceId());
            }
            targetSlug = record.slug();
            targetNamespaceId = record.namespaceId();
        } else {
            targetNamespaceId = account.defaultNamespaceId();
            record = catalog.resolve(accountId, targetNamespaceId).orElse(null);
            targetSlug = record != null ? record.slug() : "default";
        }

        // Validate Token Allow-Sets (ns and nsid claims)
        validateTokenAllowSets(tokenClaims, targetSlug, targetNamespaceId);

        if (record != null) {
            catalog.recordAccess(record.namespaceId());
        }

        NamespaceResolver resolver = registry.namespaceResolver();
        SpectorMemory memory = resolver.resolve(accountId, targetNamespaceId);
        AutoCloseable lease = memory != null ? memory.acquireLease() : null;

        // Perform Region 24 copy-once migration on default namespace bind
        if (identityPlane != null && ("default".equals(targetSlug) || targetNamespaceId.equals(account.defaultNamespaceId()))) {
            identityPlane.checkAndMigrateRegion24(accountId, memory);
        }

        // Assemble Soul Stack and Request Context
        SoulContext primarySoul = identityPlane != null ? identityPlane.primarySoulFor(accountId).orElse(null) : null;
        List<SoulContext> soulStack = identityPlane != null
                ? identityPlane.soulsFor(tokenClaims.tenantId(), tokenClaims.orgUnitIds(), accountId)
                : List.of();

        // Authorize access on target namespace — fail-closed (ADR-0029 §24)
        Optional<com.spectrayan.spector.synapse.catalog.Grant> authGrant = catalog.authorize(accountId, targetNamespaceId, GrantRole.READER);
        if (authGrant.isEmpty()) {
            log.warn("[MemoryRequestBinder] Access denied: account={} has no grant on namespace={}", accountId, targetNamespaceId);
            throw new com.spectrayan.spector.synapse.catalog.exception.NamespaceAccessDeniedException(targetNamespaceId, accountId);
        }
        GrantRole role = authGrant.get().role();

        RequestMemoryContext requestContext = new RequestMemoryContext(
                tokenClaims.tenantId(),
                tokenClaims.orgUnitIds(),
                accountId,
                targetNamespaceId,
                targetSlug,
                role,
                tokenClaims.allowSet(),
                sessionId,
                soulStack,
                primarySoul
        );

        return new MemoryBinding(memory, accountId, targetNamespaceId, targetSlug, lease, requestContext);
    }

    /**
     * Unbinds the given memory binding and safely releases its lease handle.
     *
     * @param binding the binding to release
     */
    public void unbind(MemoryBinding binding) {
        if (binding != null && binding.lease() != null) {
            try {
                binding.lease().close();
                log.trace("[MemoryRequestBinder] Released memory lease for account={}, ns={}",
                        binding.accountId(), binding.namespaceId());
            } catch (Exception e) {
                log.warn("[MemoryRequestBinder] Failed to release memory lease: {}", e.getMessage());
            }
        }
    }

    private void validateTokenAllowSets(TokenClaims claims, String targetSlug, String targetNamespaceId) {
        if (claims.nsSlugs() != null && !claims.nsSlugs().isEmpty()) {
            if (!claims.nsSlugs().contains(targetSlug)) {
                log.warn("[TokenLock] Slug '{}' is outside token allowed slugs {}", targetSlug, claims.nsSlugs());
                throw new TokenNamespaceLockedException("ns=" + claims.nsSlugs(), targetSlug);
            }
        }
        if (claims.nsIds() != null && !claims.nsIds().isEmpty()) {
            if (!claims.nsIds().contains(targetNamespaceId)) {
                log.warn("[TokenLock] NamespaceId '{}' is outside token allowed IDs {}", targetNamespaceId, claims.nsIds());
                throw new TokenNamespaceLockedException("nsid=" + claims.nsIds(), targetSlug);
            }
        }
    }

    private TokenClaims extractTokenClaims(Authentication auth) {
        Jwt jwt = null;
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            jwt = jwtAuth.getToken();
        } else if (auth.getPrincipal() instanceof Jwt principalJwt) {
            jwt = principalJwt;
        } else if (auth.getCredentials() instanceof Jwt credJwt) {
            jwt = credJwt;
        }

        if (jwt == null) {
            return new TokenClaims(null, List.of(), Set.of(), null, null);
        }

        String tenantId = jwt.getClaimAsString("tid");
        List<String> orgUnitIds = jwt.getClaimAsStringList("org");
        if (orgUnitIds == null) {
            orgUnitIds = List.of();
        }

        List<String> nsSlugs = jwt.getClaimAsStringList("ns");
        List<String> nsIds = jwt.getClaimAsStringList("nsid");

        Set<String> combinedAllowSet = new HashSet<>();
        if (nsSlugs != null) {
            combinedAllowSet.addAll(nsSlugs);
        }
        if (nsIds != null) {
            combinedAllowSet.addAll(nsIds);
        }

        return new TokenClaims(tenantId, orgUnitIds, Collections.unmodifiableSet(combinedAllowSet), nsSlugs, nsIds);
    }

    private record TokenClaims(
            String tenantId,
            List<String> orgUnitIds,
            Set<String> allowSet,
            List<String> nsSlugs,
            List<String> nsIds
    ) {}
}
