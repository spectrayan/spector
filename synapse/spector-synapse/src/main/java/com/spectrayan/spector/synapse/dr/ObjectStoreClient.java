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
package com.spectrayan.spector.synapse.dr;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Common abstraction for object storage operations supporting Disaster Recovery snapshot export,
 * rehydration restore, and compliance prefix erasure (ADR-0034 §11.2, §14, §16, Req R1.1, R1.5, R6.3).
 */
public interface ObjectStoreClient extends AutoCloseable {

    /**
     * Uploads object content as bytes with metadata.
     */
    void putObject(String bucket, String key, byte[] content, String contentType, Map<String, String> metadata);

    /**
     * Uploads object content from an input stream with specified length.
     */
    void putObject(String bucket, String key, InputStream content, long contentLength, String contentType, Map<String, String> metadata);

    /**
     * Retrieves object content as bytes.
     */
    Optional<byte[]> getObject(String bucket, String key);

    /**
     * Retrieves object content as an input stream.
     */
    Optional<InputStream> getObjectStream(String bucket, String key);

    /**
     * Lists object keys under the given prefix.
     */
    List<String> listKeysByPrefix(String bucket, String prefix);

    /**
     * Deletes all objects under the given prefix (compliance erasure and cleanup).
     *
     * @return count of deleted objects
     */
    int deleteByPrefix(String bucket, String prefix);

    /**
     * Deletes a single object by key.
     *
     * @return true if deleted or did not exist, false if failed
     */
    boolean deleteObject(String bucket, String key);

    /**
     * Checks if an object exists.
     */
    boolean objectExists(String bucket, String key);

    /**
     * The regional bucket location associated with this client.
     */
    String getRegion();

    /**
     * Configured bandwidth transfer rate limit in bytes per second (0 = unlimited).
     */
    long getBandwidthLimitBytesPerSec();

    @Override
    default void close() {}
}
