/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.spectrayan.spector.connector.sink;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Functional interface for cold tier rehydration.
 *
 * <p>This hook is called by {@link TenantMemoryRegistry} when a tenant's
 * local directory does not exist. If the tenant's data is archived in
 * cold storage, the implementation downloads and extracts it to the
 * target path.</p>
 *
 * <p>Implemented in the management layer by {@code ColdTierRehydrator}
 * and wired into the registry via {@code setColdTierHook()}.</p>
 */
public interface ColdTierRehydratorHook {

    /**
     * Checks if the tenant has data archived in cold storage.
     *
     * @param tenantId the tenant ID
     * @return true if archived and rehydratable
     */
    boolean isArchived(String tenantId);

    /**
     * Rehydrates a tenant's data from cold storage to the local path.
     *
     * @param tenantId  the tenant ID
     * @param targetDir the local directory to restore into
     * @return the path where data was restored (may equal targetDir)
     * @throws IOException on rehydration failure
     */
    Path rehydrate(String tenantId, Path targetDir) throws IOException;
}
