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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.config.properties.AuthProperties;
import com.spectrayan.spector.config.properties.CorsProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.config.properties.RateLimitProperties;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

/**
 * Externalized configuration for Spector Synapse server application.
 *
 * <p>Extends {@link SpectorConfigProperties} (Tier 2 Spring Starter properties)
 * to inherit {@code memory}, {@code embedding}, {@code metrics}, and {@code client}
 * configurations with zero field duplication.</p>
 *
 * <p>Adds Synapse server application settings: {@code port}, {@code apiKey},
 * {@code dataDir}, {@code cors}, {@code auth}, and {@code rateLimit}.</p>
 */
@Primary
@ConfigurationProperties(prefix = "spector")
public class SynapseProperties extends SpectorConfigProperties {

    /**
     * Fallback data directory when {@code spector.data-dir} is unset. Declared once here so the
     * literal is not duplicated across the resolver, migrator, detector, and CLI (Req R3.4).
     */
    public static final String DEFAULT_DATA_DIR = "./spector-data";

    private int port = 7070;
    private String apiKey = "spector-dev-key";
    private String dataDir = DEFAULT_DATA_DIR;
    private CorsProperties cors = new CorsProperties();
    private AuthProperties auth = new AuthProperties();
    private RateLimitProperties rateLimit = new RateLimitProperties();
    private com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties cache = new com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties();
    private com.spectrayan.spector.synapse.config.cell.CellProperties cell = new com.spectrayan.spector.synapse.config.cell.CellProperties();
    private com.spectrayan.spector.synapse.config.routing.RoutingProperties routing = new com.spectrayan.spector.synapse.config.routing.RoutingProperties();
    private com.spectrayan.spector.synapse.config.replication.ReplicationProperties replication = new com.spectrayan.spector.synapse.config.replication.ReplicationProperties();
    private com.spectrayan.spector.synapse.config.failover.FailoverProperties failover = new com.spectrayan.spector.synapse.config.failover.FailoverProperties();
    private com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties dr = new com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties();

    public SynapseProperties() {}

    public SynapseProperties(
            int port,
            String apiKey,
            String dataDir,
            MemoryProperties memory,
            CorsProperties cors,
            AuthProperties auth
    ) {
        if (port > 0) this.port = port;
        if (apiKey != null && !apiKey.isBlank()) this.apiKey = apiKey;
        if (dataDir != null && !dataDir.isBlank()) this.dataDir = dataDir;
        if (memory != null) setMemory(memory);
        if (cors != null) this.cors = cors;
        if (auth != null) this.auth = auth;
    }

    public int getPort() { return port; }
    public void setPort(int port) { if (port > 0) this.port = port; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { if (apiKey != null) this.apiKey = apiKey; }

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { if (dataDir != null) this.dataDir = dataDir; }

    public CorsProperties getCors() { return cors; }
    public void setCors(CorsProperties cors) { if (cors != null) this.cors = cors; }

    public AuthProperties getAuth() { return auth; }
    public void setAuth(AuthProperties auth) { if (auth != null) this.auth = auth; }

    public RateLimitProperties getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimitProperties rateLimit) { if (rateLimit != null) this.rateLimit = rateLimit; }

    public com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties getCache() { return cache; }
    public void setCache(com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties cache) { if (cache != null) this.cache = cache; }

    public com.spectrayan.spector.synapse.config.cell.CellProperties getCell() { return cell; }
    public void setCell(com.spectrayan.spector.synapse.config.cell.CellProperties cell) { if (cell != null) this.cell = cell; }

    public com.spectrayan.spector.synapse.config.routing.RoutingProperties getRouting() { return routing; }
    public void setRouting(com.spectrayan.spector.synapse.config.routing.RoutingProperties routing) { if (routing != null) this.routing = routing; }

    public com.spectrayan.spector.synapse.config.replication.ReplicationProperties getReplication() { return replication; }
    public void setReplication(com.spectrayan.spector.synapse.config.replication.ReplicationProperties replication) { if (replication != null) this.replication = replication; }

    public com.spectrayan.spector.synapse.config.failover.FailoverProperties getFailover() { return failover; }
    public void setFailover(com.spectrayan.spector.synapse.config.failover.FailoverProperties failover) { if (failover != null) this.failover = failover; }

    public com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties getDr() { return dr; }
    public void setDr(com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties dr) { if (dr != null) this.dr = dr; }

    // ══════════════════════════════════════════════════════════════
    // Canonical storage roots (ADR-0034 D1, Req R3.1, R3.4)
    // ══════════════════════════════════════════════════════════════

    /**
     * The rememberer (data-plane) root: the single source of truth for where namespace directories
     * live. Every component that resolves, migrates, or inspects a rememberer path MUST use this
     * accessor — the resolver, the migrator, the startup detector, and the CLI (Req R3.1).
     *
     * <p>Derived from {@code spector.memory.persistence-path}, falling back to
     * {@code spector.data-dir}. The leaf name is deliberately <strong>not</strong> fixed: Synapse's
     * shipped {@code application.yml} resolves this to {@code ${SPECTOR_DATA_DIR}/memory}, while
     * the framework default is {@code .spector/memory}. No caller may hardcode either leaf
     * (Req R3.4).</p>
     *
     * <p>This is intentionally <em>not</em> the same as {@link #identityRoot()}. Conflating the two
     * is what made the migrator and detector operate on a tree the resolver never reads.</p>
     *
     * @return the absolute-or-relative rememberer root directory
     */
    public java.nio.file.Path remembererRoot() {
        String path = getMemory() != null ? getMemory().getPersistencePath() : null;
        if (path == null || path.isBlank()) {
            path = getDataDir();
        }
        if (path == null || path.isBlank()) {
            path = DEFAULT_DATA_DIR;
        }
        return java.nio.file.Path.of(path);
    }

    /**
     * The identity-plane root, holding {@code identity/} and the catalog database. Derived from
     * {@code spector.data-dir} and unrelated to {@link #remembererRoot()} (ADR-0029 §23.2).
     *
     * @return the identity root directory
     */
    public java.nio.file.Path identityRoot() {
        String dir = getDataDir();
        if (dir == null || dir.isBlank()) {
            dir = DEFAULT_DATA_DIR;
        }
        return java.nio.file.Path.of(dir);
    }

    // Record-style accessors for backward compatibility across existing call sites
    public int port() { return getPort(); }
    public String apiKey() { return getApiKey(); }
    public String dataDir() { return getDataDir(); }
    public MemoryProperties memory() { return getMemory(); }
    public CorsProperties cors() { return getCors(); }
    public AuthProperties auth() { return getAuth(); }
    public RateLimitProperties rateLimit() { return getRateLimit(); }
    public com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties cache() { return getCache(); }
    public com.spectrayan.spector.synapse.config.cell.CellProperties cell() { return getCell(); }
    public com.spectrayan.spector.synapse.config.replication.ReplicationProperties replication() { return getReplication(); }
    public com.spectrayan.spector.synapse.config.failover.FailoverProperties failover() { return getFailover(); }
    public com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties dr() { return getDr(); }
}
