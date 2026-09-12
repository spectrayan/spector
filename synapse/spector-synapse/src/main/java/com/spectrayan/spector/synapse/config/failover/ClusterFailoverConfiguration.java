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
package com.spectrayan.spector.synapse.config.failover;

import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.synapse.cluster.failover.CandidateDataVerifier;
import com.spectrayan.spector.synapse.cluster.failover.FailoverOrchestrator;
import com.spectrayan.spector.synapse.cluster.failover.NodeHealthProbe;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.cell.ClusterControlPlaneConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

/**
 * Spring auto-configuration for Cell Failover and promotion orchestration (ADR-0034 §11.1, Req R4, Phase 4).
 *
 * <p>Wires:
 * <ul>
 *   <li>{@link NodeHealthProbe} (readiness failure detection)</li>
 *   <li>{@link CandidateDataVerifier} (pre-promotion data safety validation)</li>
 *   <li>{@link FailoverOrchestrator.NamespaceInventory} (discovery of namespaces from storage root)</li>
 *   <li>{@link FailoverOrchestrator} (coordinator-driven epoch advancement & promotion)</li>
 *   <li>Scheduled health evaluation loop running on coordinator</li>
 * </ul>
 * </p>
 *
 * <p>Gated on {@link ClusterControlPlaneConfiguration.ControlPlaneCondition} to guarantee
 * zero cluster failover infrastructure is allocated in standalone mode.</p>
 */
@Configuration
@Conditional(ClusterControlPlaneConfiguration.ControlPlaneCondition.class)
public class ClusterFailoverConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ClusterFailoverConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public NodeHealthProbe nodeHealthProbe() {
        return nodeId -> true;
    }

    @Bean
    @ConditionalOnMissingBean
    public CandidateDataVerifier candidateDataVerifier() {
        return (namespaceId, candidateNodeId) -> true;
    }

    @Bean
    @ConditionalOnMissingBean
    public FailoverOrchestrator.NamespaceInventory namespaceInventory(SynapseProperties properties) {
        return () -> {
            Path root = properties.remembererRoot();
            if (!Files.isDirectory(root)) {
                return List.of();
            }
            try (Stream<Path> stream = Files.list(root)) {
                return stream.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .toList();
            } catch (IOException e) {
                log.warn("[ClusterFailoverConfiguration] Failed to list namespaces from {}: {}", root, e.getMessage());
                return List.of();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public FailoverOrchestrator failoverOrchestrator(
            ControlStore controlStore,
            CoordinatorLeaseManager coordinatorLeaseManager,
            OverrideLeaseManager overrideLeaseManager,
            FenceTokenManager fenceTokenManager,
            SynapseProperties properties,
            NodeHealthProbe healthProbe,
            CandidateDataVerifier dataVerifier,
            FailoverOrchestrator.NamespaceInventory namespaceInventory
    ) {
        FailoverProperties failoverProps = properties.getFailover();
        log.info("[ClusterFailoverConfiguration] Initializing FailoverOrchestrator (failAfter={}s, mode={})",
                failoverProps.getFailAfterSeconds(), failoverProps.getMode());

        return new FailoverOrchestrator(
                controlStore,
                coordinatorLeaseManager,
                overrideLeaseManager,
                fenceTokenManager,
                failoverProps,
                healthProbe,
                dataVerifier,
                namespaceInventory,
                Clock.systemUTC()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public FailoverHealthEvaluationScheduler failoverHealthEvaluationScheduler(
            FailoverOrchestrator orchestrator,
            CoordinatorLeaseManager coordinatorLeaseManager
    ) {
        return new FailoverHealthEvaluationScheduler(orchestrator, coordinatorLeaseManager);
    }

    public static class FailoverHealthEvaluationScheduler {
        private final FailoverOrchestrator orchestrator;
        private final CoordinatorLeaseManager coordinatorLeaseManager;

        public FailoverHealthEvaluationScheduler(FailoverOrchestrator orchestrator, CoordinatorLeaseManager coordinatorLeaseManager) {
            this.orchestrator = orchestrator;
            this.coordinatorLeaseManager = coordinatorLeaseManager;
        }

        @Scheduled(fixedDelayString = "${spector.failover.probe-interval-ms:1000}")
        public void evaluateHealth() {
            if (coordinatorLeaseManager.isCoordinator()) {
                try {
                    orchestrator.evaluateCellHealth();
                } catch (Exception e) {
                    log.error("[FailoverHealthEvaluationScheduler] Error during cell health evaluation: {}", e.getMessage(), e);
                }
            }
        }
    }
}
