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
package com.spectrayan.spector.synapse.config.cell;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.cluster.store.FileControlStore;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Spring configuration wiring the cluster control plane foundation (G0, ADR-0034).
 *
 * <p>Instantiates and binds:
 * <ul>
 *   <li>{@link ControlStore} (authoritative coordinator lease, epochs, and overrides)</li>
 *   <li>{@link CoordinatorLeaseManager} (leader election & heartbeat lifecycle)</li>
 *   <li>{@link FenceTokenManager} (local fence validation & monotonicity enforcement)</li>
 *   <li>{@link OverrideLeaseManager} (override-beats-hash failover routing pins)</li>
 *   <li>3-argument {@link OwnershipResolver} wired with {@link OverrideLeaseManager}</li>
 * </ul>
 * </p>
 *
 * <p>Gated on {@link ControlPlaneCondition} (evaluating {@code spector.cluster.enabled}
 * or non-standalone {@code spector.cell.role}) to guarantee zero cluster infrastructure
 * is allocated in standalone mode.</p>
 */
@Configuration
@Conditional(ClusterControlPlaneConfiguration.ControlPlaneCondition.class)
public class ClusterControlPlaneConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ClusterControlPlaneConfiguration.class);

    public static class ControlPlaneCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String enabledStr = context.getEnvironment().getProperty("spector.cluster.enabled");
            if (Boolean.parseBoolean(enabledStr)) {
                return true;
            }
            String role = context.getEnvironment().getProperty("spector.cell.role");
            if (role == null || role.isBlank()) {
                return false;
            }
            return !"standalone".equalsIgnoreCase(role.trim());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ControlStore controlStore(SynapseProperties properties, Environment env) {
        String storeType = env.getProperty(
                SpectorPropertyConstants.CONTROL_STORE_TYPE,
                SpectorPropertyConstants.DEFAULT_CONTROL_STORE_TYPE
        );
        String filePathStr = env.getProperty(SpectorPropertyConstants.CONTROL_STORE_FILE_PATH);

        if ("memory".equalsIgnoreCase(storeType)) {
            log.info("[ClusterControlPlaneConfiguration] Initializing InMemoryControlStore");
            return new InMemoryControlStore();
        }

        Path stateFile;
        if (filePathStr != null && !filePathStr.isBlank()) {
            stateFile = Path.of(filePathStr);
        } else {
            Path root = properties != null ? properties.remembererRoot() : Path.of(System.getProperty("java.io.tmpdir"));
            stateFile = root.resolve(".control_store").resolve("control_state.json");
        }

        log.info("[ClusterControlPlaneConfiguration] Initializing FileControlStore at {}", stateFile);
        return new FileControlStore(stateFile);
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    public CoordinatorLeaseManager coordinatorLeaseManager(
            ControlStore controlStore,
            SynapseProperties properties,
            Environment env
    ) {
        String nodeId = properties.getCell() != null ? properties.getCell().resolveEffectiveNodeId() : "default-node";
        long leaseDurationSec = env.getProperty(
                SpectorPropertyConstants.COORDINATOR_LEASE_DURATION_SECONDS,
                Long.class,
                SpectorPropertyConstants.DEFAULT_COORDINATOR_LEASE_DURATION_SECONDS
        );
        long renewIntervalSec = env.getProperty(
                SpectorPropertyConstants.COORDINATOR_RENEW_INTERVAL_SECONDS,
                Long.class,
                SpectorPropertyConstants.DEFAULT_COORDINATOR_RENEW_INTERVAL_SECONDS
        );

        log.info("[ClusterControlPlaneConfiguration] Initializing CoordinatorLeaseManager for node '{}' (duration={}s, renew={}s)",
                nodeId, leaseDurationSec, renewIntervalSec);

        return new CoordinatorLeaseManager(
                controlStore,
                nodeId,
                Duration.ofSeconds(leaseDurationSec),
                Duration.ofSeconds(renewIntervalSec)
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public FenceTokenManager fenceTokenManager(ControlStore controlStore) {
        log.info("[ClusterControlPlaneConfiguration] Initializing FenceTokenManager");
        return new FenceTokenManager(controlStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public OverrideLeaseManager overrideLeaseManager(
            ControlStore controlStore,
            CoordinatorLeaseManager coordinatorLeaseManager,
            Environment env
    ) {
        long ttlSec = env.getProperty(
                SpectorPropertyConstants.OVERRIDE_TTL_SECONDS,
                Long.class,
                SpectorPropertyConstants.DEFAULT_OVERRIDE_TTL_SECONDS
        );

        log.info("[ClusterControlPlaneConfiguration] Initializing OverrideLeaseManager (defaultTtl={}s)", ttlSec);
        return new OverrideLeaseManager(
                controlStore,
                coordinatorLeaseManager,
                Duration.ofSeconds(ttlSec)
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public OwnershipResolver ownershipResolver(
            SynapseProperties properties,
            OverrideLeaseManager overrideLeaseManager
    ) {
        if (properties.getCell() != null) {
            log.info("[ClusterControlPlaneConfiguration] Building 3-arg OwnershipResolver with OverrideLeaseManager");
            return properties.getCell().toOwnershipResolver(overrideLeaseManager);
        }
        return OwnershipResolver.standalone();
    }
}
