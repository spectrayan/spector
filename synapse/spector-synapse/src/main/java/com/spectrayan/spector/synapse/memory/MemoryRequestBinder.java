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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.synapse.cluster.exception.NamespaceNotOwnedException;
import com.spectrayan.spector.synapse.cluster.exception.StaleRouteException;
import com.spectrayan.spector.synapse.cluster.fencing.FencedException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceTombstonedException;
import com.spectrayan.spector.synapse.catalog.exception.TokenNamespaceLockedException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.identity.IdentityPlane;

/**
 * Shared binder that resolves an authenticated request to a {@link MemoryBinding}
 * holding the target {@link SpectorMemory}, lease, and security context (ADR-0029 §16).
 *
 * <p>Enforces authoritative cell namespace ownership via {@link OwnershipResolver} at this
 * single choke point prior to opening the rememberer or attaching memory (ADR-0034 §15.2, Req R6.1).</p>
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
    private final OwnershipResolver ownershipResolver;
    private final MeterRegistry meterRegistry;
    private final FenceTokenManager fenceTokenManager;

    @Autowired
    public MemoryRequestBinder(
            AccountCatalog catalog,
            MemoryRegistry registry,
            SynapseProperties synapseProps,
            ObjectProvider<SpectorMemory> sharedMemoryProvider,
            ObjectProvider<IdentityPlane> identityPlaneProvider,
            ObjectProvider<OwnershipResolver> ownershipResolverProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<FenceTokenManager> fenceTokenManagerProvider) {
        this.catalog = catalog;
        this.registry = registry;
        this.synapseProps = synapseProps;
        this.sharedMemory = sharedMemoryProvider != null ? sharedMemoryProvider.getIfAvailable() : null;
        this.identityPlane = identityPlaneProvider != null ? identityPlaneProvider.getIfAvailable() : null;
        this.ownershipResolver = (ownershipResolverProvider != null && ownershipResolverProvider.getIfAvailable() != null)
                ? ownershipResolverProvider.getIfAvailable()
                : (synapseProps != null && synapseProps.cell() != null ? synapseProps.cell().toOwnershipResolver() : OwnershipResolver.standalone());
        this.meterRegistry = meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null;
        this.fenceTokenManager = fenceTokenManagerProvider != null ? fenceTokenManagerProvider.getIfAvailable() : null;

        if (this.meterRegistry != null) {
            this.meterRegistry.gauge("spector.ns.owner", this, binder -> {
                NamespaceResolver res = binder.registry != null ? binder.registry.namespaceResolver() : null;
                return res != null ? res.cachedInstanceCount() : 0;
            });
        }
    }

    public MemoryRequestBinder(
            AccountCatalog catalog,
            MemoryRegistry registry,
            SynapseProperties synapseProps,
            ObjectProvider<SpectorMemory> sharedMemoryProvider,
            ObjectProvider<IdentityPlane> identityPlaneProvider) {
        this(catalog, registry, synapseProps, sharedMemoryProvider, identityPlaneProvider, null, null, null);
    }

    public MemoryRequestBinder(
            AccountCatalog catalog,
            MemoryRegistry registry,
            SynapseProperties synapseProps,
            OwnershipResolver ownershipResolver) {
        this(catalog, registry, synapseProps, ownershipResolver, null);
    }

    public MemoryRequestBinder(
            AccountCatalog catalog,
            MemoryRegistry registry,
            SynapseProperties synapseProps,
            OwnershipResolver ownershipResolver,
            FenceTokenManager fenceTokenManager) {
        this.catalog = catalog;
        this.registry = registry;
        this.synapseProps = synapseProps;
        this.sharedMemory = null;
        this.identityPlane = null;
        this.ownershipResolver = ownershipResolver != null ? ownershipResolver : OwnershipResolver.standalone();
        this.meterRegistry = null;
        this.fenceTokenManager = fenceTokenManager;
    }

    public OwnershipResolver ownershipResolver() {
        return ownershipResolver;
    }

    public FenceTokenManager fenceTokenManager() {
        return fenceTokenManager;
    }

    private void checkDefaultOwnership() {
        if (ownershipResolver.identity().role() != NodeRole.STANDALONE) {
            String cellId = synapseProps != null && synapseProps.cell() != null ? synapseProps.cell().getId() : null;
            RoutingKey routingKey = new RoutingKey(cellId, null, "default");
            enforceOwnership(routingKey);
        }
    }

    public void enforceOwnership(RoutingKey routingKey, Long incomingEpoch) {
        if (ownershipResolver.identity().role() == NodeRole.STANDALONE) {
            return;
        }
        long startNanos = System.nanoTime();
        RouteBinding routeBinding = ownershipResolver.resolve(routingKey);
        long elapsedNanos = System.nanoTime() - startNanos;
        if (meterRegistry != null) {
            meterRegistry.timer("spector.route.lookup", "mode", routeBinding.mode().name().toLowerCase())
                    .record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }

        if (!ownershipResolver.ownsLocally(routingKey)) {
            if (meterRegistry != null) {
                meterRegistry.counter("spector.route.not_owner",
                        "namespace", routingKey.namespaceId(),
                        "owner", routeBinding.ownerId()
                ).increment();
            }
            log.warn("[MemoryRequestBinder] Refusing access to namespace '{}': owned by node '{}' at epoch {} (this node is '{}', role='{}')",
                    routingKey.namespaceId(), routeBinding.ownerId(), routeBinding.epoch(),
                    ownershipResolver.identity().nodeId(), ownershipResolver.identity().role());
            throw new NamespaceNotOwnedException(routingKey.namespaceId(), routeBinding.ownerId(), routeBinding.epoch());
        }

        // Owner-side epoch validation (Req R7.3, Invariant K2)
        if (incomingEpoch != null && incomingEpoch < routeBinding.epoch()) {
            if (meterRegistry != null) {
                meterRegistry.counter("spector.route.stale",
                        "namespace", routingKey.namespaceId(),
                        "owner", routeBinding.ownerId()
                ).increment();
            }
            log.warn("[MemoryRequestBinder] Rejecting stale route for namespace '{}': incoming epoch {} is older than active epoch {} (owner='{}')",
                    routingKey.namespaceId(), incomingEpoch, routeBinding.epoch(), routeBinding.ownerId());
            throw new StaleRouteException(routingKey.namespaceId(), routeBinding.ownerId(), incomingEpoch, routeBinding.epoch());
        }
    }

    private void enforceOwnership(RoutingKey routingKey) {
        Long incomingEpoch = extractIncomingEpoch();
        enforceOwnership(routingKey, incomingEpoch);
    }

    private Long extractIncomingEpoch() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                String epochHeader = servletAttrs.getRequest().getHeader("X-Spector-Epoch");
                if (epochHeader != null && !epochHeader.isBlank()) {
                    return Long.parseLong(epochHeader.trim());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void enforceFence(String namespaceId, String incomingFence) {
        if (ownershipResolver.identity().role() == NodeRole.STANDALONE) {
            return;
        }
        if (fenceTokenManager != null && fenceTokenManager.hasLocalFence(namespaceId)) {
            if (!fenceTokenManager.validateFence(namespaceId, incomingFence)) {
                if (meterRegistry != null) {
                    meterRegistry.counter("spector.route.fenced",
                            "namespace", namespaceId
                    ).increment();
                }
                long activeEpoch = fenceTokenManager.getLocalFence(namespaceId);
                log.warn("[MemoryRequestBinder] Fence mismatch for namespace '{}': incoming '{}' does not match active epoch {}",
                        namespaceId, incomingFence, activeEpoch);
                throw new FencedException(namespaceId, incomingFence, activeEpoch);
            }
        }
    }

    public String extractIncomingFence() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                String fenceHeader = servletAttrs.getRequest().getHeader("X-Spector-Fence");
                if (fenceHeader != null && !fenceHeader.isBlank()) {
                    return fenceHeader.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public MemoryBinding bind(Authentication auth, Optional<String> selector) {
        return bind(auth, selector, null);
    }

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
            checkDefaultOwnership();
            AutoCloseable lease = sharedMemory != null ? sharedMemory.acquireLease() : null;
            RequestMemoryContext reqCtx = new RequestMemoryContext(
                    null, List.of(), DEFAULT_USER_ID, DEFAULT_USER_ID, "default",
                    GrantRole.OWNER, Set.of(), sessionId, List.of(), null);
            return new MemoryBinding(sharedMemory, DEFAULT_USER_ID, DEFAULT_USER_ID, "default", lease, reqCtx);
        }

        String accountId = auth.getName();
        if (accountId == null || accountId.isBlank() || DEFAULT_USER_ID.equals(accountId)) {
            checkDefaultOwnership();
            AutoCloseable lease = sharedMemory != null ? sharedMemory.acquireLease() : null;
            RequestMemoryContext reqCtx = new RequestMemoryContext(
                    null, List.of(), DEFAULT_USER_ID, DEFAULT_USER_ID, "default",
                    GrantRole.OWNER, Set.of(), sessionId, List.of(), null);
            return new MemoryBinding(sharedMemory, DEFAULT_USER_ID, DEFAULT_USER_ID, "default", lease, reqCtx);
        }

        TokenClaims tokenClaims = extractTokenClaims(auth);
        Account account = catalog.getOrCreateAccount(accountId, tokenClaims.profile(), tokenClaims.kind());
        if (account == null) {
            account = catalog.getOrCreateAccount(accountId);
        }

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

        validateTokenAllowSets(tokenClaims, targetSlug, targetNamespaceId);

        if (tokenClaims.tenantId() != null && !tokenClaims.tenantId().equals(account.tenantId())) {
            log.warn("[MemoryRequestBinder] Access denied: token tenantId='{}' does not match account tenantId='{}' for account={}",
                    tokenClaims.tenantId(), account.tenantId(), accountId);
            throw new com.spectrayan.spector.synapse.catalog.exception.NamespaceAccessDeniedException(
                    targetNamespaceId, accountId);
        }

        Optional<com.spectrayan.spector.synapse.catalog.Grant> authGrant =
                catalog.authorize(accountId, targetNamespaceId, GrantRole.READER);
        if (authGrant.isEmpty()) {
            log.warn("[MemoryRequestBinder] Access denied: account={} has no grant on namespace={}",
                    accountId, targetNamespaceId);
            throw new com.spectrayan.spector.synapse.catalog.exception.NamespaceAccessDeniedException(
                    targetNamespaceId, accountId);
        }
        GrantRole role = authGrant.get().role();

        NamespaceResolver resolver = registry.namespaceResolver();
        String ownerAccountId = record != null ? record.ownerAccountId() : accountId;
        String routingTenantId = (resolver != null)
                ? resolver.placementTenantIdFor(targetNamespaceId, ownerAccountId, accountId, account)
                : (account != null ? account.tenantId() : null);

        String cellId = synapseProps != null && synapseProps.cell() != null ? synapseProps.cell().getId() : null;
        RoutingKey routingKey = new RoutingKey(cellId, routingTenantId, targetNamespaceId);
        enforceOwnership(routingKey);

        if (role == GrantRole.WRITER || role == GrantRole.ADMIN) {
            String incomingFence = extractIncomingFence();
            enforceFence(targetNamespaceId, incomingFence);
        }

        if (record != null) {
            catalog.recordAccess(record.namespaceId());
        }

        SpectorMemory memory = resolver != null ? resolver.resolve(accountId, targetNamespaceId) : null;
        AutoCloseable lease = memory != null ? memory.acquireLease() : null;

        try {
            if (identityPlane != null && ("default".equals(targetSlug)
                    || targetNamespaceId.equals(account.defaultNamespaceId()))) {
                identityPlane.checkAndMigrateRegion24(accountId, memory);
            }

            List<String> tokenOrgs = tokenClaims.orgUnitIds();
            List<String> catalogOrgs = catalog.orgUnitIdsForAccount(accountId);
            List<String> effectiveOrgs;
            if (catalogOrgs != null && !catalogOrgs.isEmpty()) {
                if (tokenOrgs != null && !tokenOrgs.isEmpty()) {
                    java.util.Set<String> catalogSet = new java.util.HashSet<>(catalogOrgs);
                    effectiveOrgs = tokenOrgs.stream().filter(catalogSet::contains).toList();
                } else {
                    effectiveOrgs = catalogOrgs;
                }
            } else {
                effectiveOrgs = tokenOrgs != null ? tokenOrgs : List.of();
            }

            SoulContext primarySoul = identityPlane != null
                    ? identityPlane.primarySoulFor(accountId).orElse(null) : null;
            List<SoulContext> soulStack = identityPlane != null
                    ? identityPlane.soulsFor(tokenClaims.tenantId(), effectiveOrgs, accountId)
                    : List.of();

            com.spectrayan.spector.memory.model.SalienceProfile salience = identityPlane != null
                    ? identityPlane.salienceFor(accountId).orElse(null) : null;
            if (record != null && record.bias() != null
                    && record.bias() != com.spectrayan.spector.synapse.catalog.NamespaceBias.EMPTY) {
                salience = NamespaceBiasApplier.apply(salience, record.bias());
            }

            RequestMemoryContext requestContext = new RequestMemoryContext(
                    tokenClaims.tenantId(),
                    effectiveOrgs,
                    accountId,
                    targetNamespaceId,
                    targetSlug,
                    role,
                    tokenClaims.allowSet(),
                    sessionId,
                    soulStack,
                    primarySoul,
                    salience
            );
            return new MemoryBinding(memory, accountId, targetNamespaceId, targetSlug, lease, requestContext);
        } catch (RuntimeException e) {
            if (lease != null) {
                try {
                    lease.close();
                } catch (Exception closeErr) {
                    log.warn("[MemoryRequestBinder] Failed to release lease after bind failure: {}",
                            closeErr.getMessage());
                }
            }
            throw e;
        }
    }

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
                throw new TokenNamespaceLockedException("ns=" + claims.nsSlugs(), targetSlug);
            }
        }
        if (claims.nsIds() != null && !claims.nsIds().isEmpty()) {
            if (!claims.nsIds().contains(targetNamespaceId)) {
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
            return new TokenClaims(null, List.of(), Set.of(), null, null, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN);
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

        String profileStr = jwt.getClaimAsString("profile");
        if (profileStr == null) {
            profileStr = jwt.getClaimAsString("acct_type");
        }
        AccountProfile profile = AccountProfile.HUMAN_SOLO;
        if (profileStr != null) {
            try {
                profile = AccountProfile.valueOf(profileStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        String kindStr = jwt.getClaimAsString("kind");
        PrincipalKind kind = PrincipalKind.HUMAN;
        if (kindStr != null) {
            try {
                kind = PrincipalKind.valueOf(kindStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return new TokenClaims(tenantId, orgUnitIds, Collections.unmodifiableSet(combinedAllowSet),
                nsSlugs, nsIds, profile, kind);
    }

    private record TokenClaims(
            String tenantId,
            List<String> orgUnitIds,
            Set<String> allowSet,
            List<String> nsSlugs,
            List<String> nsIds,
            AccountProfile profile,
            PrincipalKind kind
    ) {}
}
