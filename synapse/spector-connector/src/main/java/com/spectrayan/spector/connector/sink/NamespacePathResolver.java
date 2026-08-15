/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.spectrayan.spector.connector.sink;

import java.nio.file.Path;

/**
 * SPI for resolving and provisioning per-user namespace paths.
 *
 * <p>Implemented by the management layer's {@code TenantNamespaceManager}
 * and injected into {@link TenantMemoryRegistry} to enable namespace-level
 * routing without introducing a reverse module dependency.</p>
 *
 * <p>The resolver is responsible for:</p>
 * <ul>
 *   <li>Computing the deterministic filesystem path for a {@code (tenantId, namespaceId)} pair</li>
 *   <li>JIT-creating the namespace directory structure if it doesn't exist yet</li>
 *   <li>Registering the namespace in the in-memory catalog</li>
 * </ul>
 */
@FunctionalInterface
public interface NamespacePathResolver {

    /**
     * Resolves (and JIT-creates if necessary) the directory path for a namespace.
     *
     * <p>If the namespace does not yet exist on disk, the implementation should
     * create the full directory structure ({@code global/}, {@code partitions/},
     * {@code cross/}, {@code namespace.json}) before returning.</p>
     *
     * @param tenantId    the tenant identifier
     * @param namespaceId the namespace identifier (e.g., {@code user-admin})
     * @return the absolute path to the namespace directory (never null)
     * @throws RuntimeException if the namespace cannot be created
     */
    Path resolveAndProvision(String tenantId, String namespaceId);
}
