/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.replication;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for fetching archived sealed partition objects from a cold storage tier / object store
 * (ADR-0034 §9.7, Req R2.4, Blocker B4).
 *
 * <p>Enables replica nodes performing a full resync to fetch immutable sealed partition bundles directly
 * from cold storage (e.g. S3 / GCS / local archive) rather than forcing the partition owner to re-read
 * archived data from disk.</p>
 */
public interface ColdTierObjectSource {

    /**
     * Checks if an archived object exists in the cold tier.
     *
     * @param objectRef cold tier object reference URI/key
     * @return true if object exists and is accessible
     */
    boolean hasObject(String objectRef);

    /**
     * Downloads an archived object from the cold tier to the specified local path.
     *
     * @param objectRef   cold tier object reference URI/key
     * @param destination destination local file path
     * @throws IOException if network or disk I/O fails
     */
    void fetchObject(String objectRef, Path destination) throws IOException;

    /**
     * Returns the SHA-256 hex string of the cold tier object without requiring full local download
     * if supported by the underlying provider.
     *
     * @param objectRef cold tier object reference URI/key
     * @return SHA-256 hex string
     * @throws IOException if retrieval fails
     */
    String calculateSha256(String objectRef) throws IOException;
}
