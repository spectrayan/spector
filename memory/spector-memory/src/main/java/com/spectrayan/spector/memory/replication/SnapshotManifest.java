/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.replication;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot manifest record describing an atomic, verifiable replication point
 * (ADR-0034 §9.7, §10, Req R1.1–R1.6, Task 1.1).
 *
 * <p>Manifests are self-describing, specifying the exact path helper used by the source
 * and strictly enforcing identity plane separation (Invariant N4).</p>
 */
public record SnapshotManifest(
        String plane,
        int manifestVersion,
        String tenantId,
        String namespaceId,
        String pathHelper,
        long epoch,
        long hwm,
        SnapshotKind kind,
        RuntimeEntry runtime,
        ActivePartitionEntry activePartition,
        List<SealedPartitionEntry> sealed,
        long walFrom,
        long walTo,
        String encryptionKeyRef,
        List<FileEntry> files,
        String namespaceMetadataJson
) {

    public static final String PLANE_NAMESPACE = "namespace";
    public static final int CURRENT_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SnapshotManifest(
            String plane,
            int manifestVersion,
            String tenantId,
            String namespaceId,
            String pathHelper,
            long epoch,
            long hwm,
            SnapshotKind kind,
            RuntimeEntry runtime,
            ActivePartitionEntry activePartition,
            List<SealedPartitionEntry> sealed,
            long walFrom,
            long walTo,
            String encryptionKeyRef
    ) {
        this(plane, manifestVersion, tenantId, namespaceId, pathHelper, epoch, hwm, kind, runtime, activePartition, sealed, walFrom, walTo, encryptionKeyRef, List.of(), null);
    }

    public SnapshotManifest(
            String plane,
            int manifestVersion,
            String tenantId,
            String namespaceId,
            String pathHelper,
            long epoch,
            long hwm,
            SnapshotKind kind,
            RuntimeEntry runtime,
            ActivePartitionEntry activePartition,
            List<SealedPartitionEntry> sealed,
            long walFrom,
            long walTo,
            String encryptionKeyRef,
            List<FileEntry> files
    ) {
        this(plane, manifestVersion, tenantId, namespaceId, pathHelper, epoch, hwm, kind, runtime, activePartition, sealed, walFrom, walTo, encryptionKeyRef, files, null);
    }

    public SnapshotManifest {
        // R1.3: Enforce plane == "namespace" (N4)
        if (!PLANE_NAMESPACE.equalsIgnoreCase(plane)) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Invalid snapshot plane '" + plane + "'. Must be '" + PLANE_NAMESPACE + "' (Invariant N4)."
            );
        }

        if (manifestVersion < 1) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "manifestVersion must be >= 1, got: " + manifestVersion
            );
        }

        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        StoragePaths.validateNamespaceId(namespaceId);
        if (tenantId != null && !tenantId.isBlank()) {
            StoragePaths.validateTenantId(tenantId);
        }

        Objects.requireNonNull(pathHelper, "pathHelper must not be null");
        if (pathHelper.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "pathHelper must not be blank");
        }

        Objects.requireNonNull(kind, "kind must not be null");
        sealed = sealed != null ? List.copyOf(sealed) : List.of();
        files = files != null ? List.copyOf(files) : List.of();

        // Enforce identity plane separation on all contained references (N4)
        if (runtime != null) {
            ReplicationPathFilter.assertNotIdentityPlane(runtime.file());
        }
        if (activePartition != null) {
            ReplicationPathFilter.assertNotIdentityPlane(activePartition.id());
        }
        for (SealedPartitionEntry s : sealed) {
            ReplicationPathFilter.assertNotIdentityPlane(s.id());
            if (s.objectRef() != null) {
                ReplicationPathFilter.assertNotIdentityPlane(s.objectRef());
            }
        }
        for (FileEntry f : files) {
            ReplicationPathFilter.assertNotIdentityPlane(f.path());
        }
    }

    public record FileEntry(String path, String sha256) {
        public FileEntry {
            Objects.requireNonNull(path, "file path must not be null");
            Objects.requireNonNull(sha256, "file sha256 must not be null");
            ReplicationPathFilter.assertNotIdentityPlane(path);
        }
    }

    public record RuntimeEntry(String file, String sha256, long gen) {
        public RuntimeEntry {
            Objects.requireNonNull(file, "runtime file must not be null");
            Objects.requireNonNull(sha256, "runtime sha256 must not be null");
        }
    }

    public record ActivePartitionEntry(String id, String sha256) {
        public ActivePartitionEntry {
            Objects.requireNonNull(id, "active partition id must not be null");
            Objects.requireNonNull(sha256, "active partition sha256 must not be null");
        }
    }

    public record SealedPartitionEntry(String id, String sha256, String objectRef) {
        public SealedPartitionEntry {
            Objects.requireNonNull(id, "sealed partition id must not be null");
            Objects.requireNonNull(sha256, "sealed partition sha256 must not be null");
        }
    }

    /**
     * Serializes this manifest to a JSON string.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize SnapshotManifest to JSON", e);
        }
    }

    /**
     * Deserializes a SnapshotManifest from a JSON string.
     */
    public static SnapshotManifest fromJson(String json) {
        Objects.requireNonNull(json, "json must not be null");
        try {
            return MAPPER.readValue(json, SnapshotManifest.class);
        } catch (Exception e) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Failed to parse SnapshotManifest from JSON: " + e.getMessage()
            );
        }
    }
}
