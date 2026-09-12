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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.storage.StoragePaths;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Enforces identity-plane separation (ADR-0034 §9.3, §9.5, Req R9.1, R9.2, Invariant N4).
 *
 * <p>Ensures that identity bundles ({@code identity.bundle}) and identity catalog hierarchies
 * ({@code accounts/}, {@code tenants/}) can never be enumerated or packaged into a rememberer
 * snapshot manifest.</p>
 */
public final class ReplicationPathFilter {

    private static final String IDENTITY_BUNDLE_NAME = "identity.bundle";
    private static final String IDENTITY_MANIFEST_NAME = "identity.manifest";

    private static final Set<String> FORBIDDEN_COMPONENTS = Set.of(
            StoragePaths.DIR_ACCOUNTS.toLowerCase(),
            StoragePaths.DIR_TENANTS.toLowerCase(),
            IDENTITY_BUNDLE_NAME.toLowerCase(),
            IDENTITY_MANIFEST_NAME.toLowerCase()
    );

    private ReplicationPathFilter() {}

    /**
     * Checks whether the given path belongs to the identity plane.
     *
     * @param path path to evaluate
     * @return {@code true} if the path belongs to the identity plane, {@code false} otherwise
     */
    public static boolean isIdentityPlanePath(Path path) {
        if (path == null) {
            return false;
        }
        for (Path component : path) {
            String name = component.toString().toLowerCase();
            if (FORBIDDEN_COMPONENTS.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the given string path belongs to the identity plane.
     *
     * @param pathString string path to evaluate
     * @return {@code true} if the path string belongs to the identity plane, {@code false} otherwise
     */
    public static boolean isIdentityPlanePath(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            return false;
        }
        String normalized = pathString.replace('\\', '/').toLowerCase();
        String[] parts = normalized.split("/");
        for (String part : parts) {
            if (FORBIDDEN_COMPONENTS.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enforces that the specified path does NOT belong to the identity plane.
     *
     * @param path path to validate
     * @throws SpectorValidationException if the path touches the identity plane
     */
    public static void assertNotIdentityPlane(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        if (isIdentityPlanePath(path)) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Identity-plane path cannot be enumerated into replication manifest (Invariant N4): " + path
            );
        }
    }

    /**
     * Enforces that the specified path string does NOT belong to the identity plane.
     *
     * @param pathString string path to validate
     * @throws SpectorValidationException if the path string touches the identity plane
     */
    public static void assertNotIdentityPlane(String pathString) {
        Objects.requireNonNull(pathString, "pathString must not be null");
        if (isIdentityPlanePath(pathString)) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID,
                    "Identity-plane path cannot be enumerated into replication manifest (Invariant N4): " + pathString
            );
        }
    }
}
