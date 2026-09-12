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
package com.spectrayan.spector.synapse.config.replication;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.memory.replication.ReplicaApplyEngine;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.cell.ClusterControlPlaneConfiguration;
import com.spectrayan.spector.synapse.replication.ReplicationCoordinator;
import com.spectrayan.spector.synapse.replication.ReplicationHintWriter;
import com.spectrayan.spector.synapse.replication.ReplicationMetrics;
import com.spectrayan.spector.synapse.replication.ReplicationServer;
import com.spectrayan.spector.synapse.replication.TenantAllowListFilter;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Spring auto-configuration for Cell Replication infrastructure (ADR-0034 §10, Req R6, R7, Phase 3).
 *
 * <p>Wires:
 * <ul>
 *   <li>{@link TenantAllowListFilter} (isolation enforcement on replica)</li>
 *   <li>{@link ReplicationMetrics} (replication telemetry)</li>
 *   <li>{@link ReplicationHintWriter} (Redis :hint publication)</li>
 *   <li>{@link ReplicationCoordinator} (owner-side snapshot debouncer & trigger)</li>
 *   <li>{@link ReplicaApplyEngine} (replica-side crash-safe snapshot & WAL application)</li>
 *   <li>{@link ReplicationServer} (replica-side TCP / mTLS listener on :9090)</li>
 * </ul>
 * </p>
 *
 * <p>Gated on {@link ClusterControlPlaneConfiguration.ControlPlaneCondition} to guarantee
 * zero cluster replication infrastructure is allocated in standalone mode.</p>
 */
@Configuration
@Conditional(ClusterControlPlaneConfiguration.ControlPlaneCondition.class)
public class ClusterReplicationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ClusterReplicationConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TenantAllowListFilter tenantAllowListFilter(SynapseProperties properties, Environment env) {
        String allowedStr = env.getProperty("spector.replication.allowed-tenants");
        Set<String> tenants = new HashSet<>();
        if (allowedStr != null && !allowedStr.isBlank()) {
            for (String t : allowedStr.split(",")) {
                if (!t.isBlank()) {
                    tenants.add(t.trim());
                }
            }
        }
        log.info("[ClusterReplicationConfiguration] Initializing TenantAllowListFilter with {} tenants", tenants.size());
        return new TenantAllowListFilter(tenants);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReplicationMetrics replicationMetrics() {
        return new ReplicationMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReplicationHintWriter replicationHintWriter(ObjectProvider<RedisClient> redisClientProvider) {
        RedisClient redisClient = redisClientProvider.getIfAvailable();
        if (redisClient == null) {
            log.info("[ClusterReplicationConfiguration] RedisClient unavailable; using noop ReplicationHintWriter");
            return ReplicationHintWriter.noop();
        }
        return (key, hwm, ts, epoch) -> {
            try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
                RedisCommands<String, String> sync = conn.sync();
                String hintKey = ReplicationHintWriter.hintKeyOf(key);
                String hintValue = String.format("{\"hwm\":%d,\"ts\":%d,\"epoch\":%d}", hwm, ts, epoch);
                sync.set(hintKey, hintValue);
            } catch (Exception e) {
                log.warn("[ClusterReplicationConfiguration] Failed to write replication hint to Redis: {}", e.getMessage());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ReplicationCoordinator replicationCoordinator(
            SynapseProperties properties,
            OwnershipResolver ownershipResolver,
            ReplicationHintWriter hintWriter,
            ReplicationMetrics metrics
    ) {
        String cellId = properties.getCell() != null && properties.getCell().getId() != null
                ? properties.getCell().getId()
                : "cell-local";
        log.info("[ClusterReplicationConfiguration] Initializing ReplicationCoordinator for cell '{}'", cellId);
        return new ReplicationCoordinator(
                cellId,
                ownershipResolver,
                properties.getReplication(),
                hintWriter,
                metrics
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ReplicaApplyEngine replicaApplyEngine(
            SynapseProperties properties,
            TenantAllowListFilter allowListFilter
    ) {
        Path root = properties.remembererRoot();
        log.info("[ClusterReplicationConfiguration] Initializing ReplicaApplyEngine with persistence root: {}", root);
        return new ReplicaApplyEngine(
                root,
                "default",
                allowListFilter.getAllowedTenants(),
                Collections.emptySet()
        );
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spector.replication.server-enabled", havingValue = "true")
    public ReplicationServer replicationServer(
            SynapseProperties properties,
            TenantAllowListFilter allowListFilter,
            ReplicaApplyEngine applyEngine,
            ReplicationMetrics metrics,
            Environment env
    ) {
        ReplicationProperties repProps = properties.getReplication();
        String bindHost = repProps.getBindHost();
        int port = repProps.getPort();
        boolean insecureMode = env.getProperty("spector.replication.insecure-mode", Boolean.class, true);
        Path stagingDir = properties.remembererRoot().resolve(".replication_staging");

        log.info("[ClusterReplicationConfiguration] Initializing ReplicationServer on {}:{} (insecureMode={})",
                bindHost, port, insecureMode);

        return new ReplicationServer(
                bindHost,
                port,
                null,
                allowListFilter,
                applyEngine,
                metrics,
                stagingDir,
                insecureMode
        );
    }
}
