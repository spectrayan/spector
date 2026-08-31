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

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.exception.FederationDisabledException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes cross-rememberer federated recall across isolated memory spaces (ADR-0029 §7).
 *
 * <p>Enforces account federation flags, query and cold-open budgets, parallel fan-out
 * with virtual threads, and provenance-annotated heuristic merging.</p>
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spector.federation.enabled", havingValue = "true", matchIfMissing = false)
public class FederatedRecallService {

    private static final Logger log = LoggerFactory.getLogger(FederatedRecallService.class);

    private static final int MAX_FEDERATION_NAMESPACES = 10;
    private static final int DEFAULT_MAX_NAMESPACES = 5;

    private final AccountCatalog catalog;
    private final MemoryRegistry userMemoryRegistry;
    private final SynapseProperties synapseProps;

    @Autowired
    public FederatedRecallService(
            AccountCatalog catalog,
            MemoryRegistry userMemoryRegistry,
            SynapseProperties synapseProps) {
        this.catalog = catalog;
        this.userMemoryRegistry = userMemoryRegistry;
        this.synapseProps = synapseProps;
    }

    public FederatedRecallService(AccountCatalog catalog, MemoryRegistry userMemoryRegistry) {
        this(catalog, userMemoryRegistry, null);
    }

    /**
     * Executes federated recall across multiple namespaces on behalf of an authenticated principal.
     *
     * @param accountId the calling account ID (TSID)
     * @param request   the federated recall request parameters
     * @return the annotated federated hits and execution summary
     * @throws FederationDisabledException if the account does not have federation enabled
     * @throws SpectorValidationException if the query parameters are invalid
     */
    public FederatedRecallResponse federatedRecall(String accountId, FederatedRecallRequest request) {
        if (request == null || request.queryText() == null || request.queryText().isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "queryText");
        }

        final long startTime = System.currentTimeMillis();
        final boolean authEnabled = synapseProps == null || synapseProps.auth().enabled();

        // 1. Quota & account federation flag check
        if (authEnabled && accountId != null && !accountId.isBlank() && !"default".equals(accountId)) {
            Account account = catalog.getOrCreateAccount(accountId);
            if (account.flags() != null && !account.flags().federation()) {
                log.warn("[FederatedRecall] Account '{}' attempted federated recall but federation flag is disabled", accountId);
                throw new FederationDisabledException(accountId);
            }
        }

        // 2. Target Namespace Resolution
        final List<String> requested = request.namespaces();
        final boolean queryAllGranted = requested == null || requested.isEmpty()
                || (requested.size() == 1 && "granted".equalsIgnoreCase(requested.get(0).trim()));

        final List<TargetNamespace> candidateTargets = new ArrayList<>();
        final List<String> denied = new ArrayList<>();
        final List<String> failed = new ArrayList<>();

        if (queryAllGranted) {
            if (authEnabled && accountId != null && !accountId.isBlank() && !"default".equals(accountId)) {
                List<NamespaceRecord> accessible = catalog.listAccessible(accountId);
                for (NamespaceRecord rec : accessible) {
                    if (rec.status() == NamespaceStatus.TOMBSTONED) {
                        continue;
                    }
                    GrantRole role = catalog.authorize(accountId, rec.namespaceId(), GrantRole.READER)
                            .map(Grant::role)
                            .orElse(accountId.equals(rec.ownerAccountId()) ? GrantRole.OWNER : GrantRole.READER);
                    candidateTargets.add(new TargetNamespace(rec.namespaceId(), rec.slug(), role));
                }
            } else {
                // Anonymous or auth-disabled: query default namespace
                candidateTargets.add(new TargetNamespace("default", "default", GrantRole.OWNER));
            }
        } else {
            for (String selector : requested) {
                if (selector == null || selector.isBlank()) continue;
                String trimmed = selector.trim();
                try {
                    Optional<NamespaceRecord> recOpt = catalog.resolve(accountId, trimmed);
                    if (recOpt.isEmpty()) {
                        log.debug("[FederatedRecall] Namespace '{}' not found for account '{}'", trimmed, accountId);
                        denied.add(trimmed);
                        continue;
                    }
                    NamespaceRecord rec = recOpt.get();
                    if (rec.status() == NamespaceStatus.TOMBSTONED) {
                        failed.add(trimmed);
                        continue;
                    }
                    Optional<Grant> authOpt = catalog.authorize(accountId, rec.namespaceId(), GrantRole.READER);
                    if (authOpt.isEmpty() && !accountId.equals(rec.ownerAccountId())) {
                        denied.add(trimmed);
                        continue;
                    }
                    GrantRole role = authOpt.map(Grant::role)
                            .orElse(accountId.equals(rec.ownerAccountId()) ? GrantRole.OWNER : GrantRole.READER);
                    candidateTargets.add(new TargetNamespace(rec.namespaceId(), rec.slug(), role));
                } catch (Exception e) {
                    log.warn("[FederatedRecall] Error resolving namespace '{}': {}", trimmed, e.getMessage());
                    failed.add(trimmed);
                }
            }
        }

        // 3. Apply Namespace Caps & Cold-Open Budgets
        final int targetBudget = Math.min(candidateTargets.size(), MAX_FEDERATION_NAMESPACES);
        final int maxCold = request.maxColdOpens();
        int coldOpened = 0;

        final List<TargetNamespace> executableTargets = new ArrayList<>();
        final List<String> skippedCold = new ArrayList<>();

        for (int i = 0; i < targetBudget; i++) {
            TargetNamespace target = candidateTargets.get(i);
            boolean isHot = userMemoryRegistry.namespaceResolver() != null
                    && userMemoryRegistry.namespaceResolver().isHot(target.namespaceId);
            if (!isHot) {
                if (coldOpened >= maxCold) {
                    skippedCold.add(target.slug != null ? target.slug : target.namespaceId);
                    continue;
                }
                coldOpened++;
            }
            executableTargets.add(target);
        }

        // Record any remaining targets exceeding max federation count as skipped
        for (int i = targetBudget; i < candidateTargets.size(); i++) {
            TargetNamespace target = candidateTargets.get(i);
            skippedCold.add(target.slug != null ? target.slug : target.namespaceId);
        }

        // 4. Parallel Fan-Out Execution via ConcurrentTasks (Structured Concurrency & Virtual Threads)
        final List<String> opened = new ArrayList<>();
        final List<FederatedRecallHit> allHits = new ArrayList<>();
        final RecallOptions recallOptions = RecallOptions.builder()
                .topK(request.perNamespaceTopK())
                .profile(request.profile() != null ? request.profile() : CognitiveProfile.BALANCED)
                .scoringMode(request.scoringMode() != null ? request.scoringMode() : ScoringMode.COGNITIVE)
                .build();

        if (!executableTargets.isEmpty()) {
            final Map<String, TargetNamespace> targetMap = new LinkedHashMap<>();
            final List<ConcurrentTasks.LabeledTask<List<CognitiveResult>>> tasks = new ArrayList<>();

            for (TargetNamespace target : executableTargets) {
                String label = target.slug != null ? target.slug : target.namespaceId;
                targetMap.put(label, target);
                tasks.add(new ConcurrentTasks.LabeledTask<>(label, () -> {
                    SpectorMemory memory = userMemoryRegistry.namespaceResolver() != null
                            ? userMemoryRegistry.namespaceResolver().resolve(accountId, target.namespaceId)
                            : userMemoryRegistry.resolveFor(target.namespaceId);
                    return memory.recall(request.queryText(), recallOptions);
                }));
            }

            try {
                java.time.Duration timeout = java.time.Duration.ofMillis(request.timeoutMs());
                ConcurrentTasks.PartialResult<List<CognitiveResult>> partial = ConcurrentTasks.forkJoinPartial(tasks, timeout);

                for (ConcurrentTasks.PartialResult.Entry<List<CognitiveResult>> entry : partial.successes()) {
                    TargetNamespace target = targetMap.get(entry.label());
                    opened.add(entry.label());
                    List<CognitiveResult> results = entry.result();
                    if (results != null) {
                        for (int rank = 0; rank < results.size(); rank++) {
                            CognitiveResult cr = results.get(rank);
                            allHits.add(new FederatedRecallHit(
                                    target.namespaceId,
                                    target.slug,
                                    target.role,
                                    rank + 1,
                                    0, // Will be set after merge
                                    cr
                            ));
                        }
                    }
                }

                for (String timedOutLabel : partial.timedOut()) {
                    log.warn("[FederatedRecall] Namespace '{}' query timed out", timedOutLabel);
                    failed.add(timedOutLabel);
                }

                for (ConcurrentTasks.PartialResult.Failure failure : partial.failures()) {
                    log.warn("[FederatedRecall] Namespace '{}' query failed: {}", failure.label(), failure.cause().getMessage());
                    failed.add(failure.label());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[FederatedRecall] Federated recall interrupted: {}", ie.getMessage());
                for (TargetNamespace target : executableTargets) {
                    failed.add(target.slug != null ? target.slug : target.namespaceId);
                }
            }
        }

        // 5. Heuristic Merging & Ranking
        allHits.sort(Comparator.comparing(FederatedRecallHit::score).reversed()
                .thenComparing(FederatedRecallHit::id));

        final List<FederatedRecallHit> mergedHits = new ArrayList<>();
        final int globalLimit = Math.min(allHits.size(), request.topK());
        for (int rank = 0; rank < globalLimit; rank++) {
            FederatedRecallHit original = allHits.get(rank);
            mergedHits.add(new FederatedRecallHit(
                    original.namespaceId(),
                    original.slug(),
                    original.role(),
                    original.localRank(),
                    rank + 1,
                    original.result()
            ));
        }

        final long durationMs = System.currentTimeMillis() - startTime;
        final FederatedRecallSummary summary = new FederatedRecallSummary(
                candidateTargets.size(),
                opened,
                skippedCold,
                denied,
                failed,
                durationMs
        );

        return new FederatedRecallResponse(mergedHits, summary);
    }

    private record TargetNamespace(String namespaceId, String slug, GrantRole role) {}
    private record NamespaceExecutionResult(TargetNamespace target, List<CognitiveResult> results, Throwable error) {}
}
