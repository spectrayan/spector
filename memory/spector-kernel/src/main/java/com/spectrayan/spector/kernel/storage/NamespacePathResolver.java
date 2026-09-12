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
package com.spectrayan.spector.kernel.storage;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves rememberer directories. The ONLY sanctioned source of a namespace path
 * (ADR-0033 §9.2, Phase 0.1, Req R1.1, R1.2, R4, R9).
 */
public final class NamespacePathResolver {

    private NamespacePathResolver() {
        // utility class
    }

    /**
     * Identity recorded in {@code namespace.json} (Req R8) and in ADR-0033 §9.7 manifests (Req R8.3).
     */
    public enum Layout {
        FLAT_SHA256("StoragePaths.namespaceDirSharded"),
        TENANT_SHA256("StoragePaths.tenantRootedNamespaceDir");

        private final String id;

        Layout(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Layout fromId(String id) {
            for (Layout layout : values()) {
                if (layout.id.equals(id) || layout.name().equalsIgnoreCase(id)) {
                    return layout;
                }
            }
            throw new IllegalArgumentException("Unknown layout id: " + id);
        }
    }

    /**
     * Placement represents the resolved physical directory path together with its layout metadata,
     * tenant, and namespace identifier.
     *
     * @param dir         the absolute or base-resolved physical directory path
     * @param layout      the layout strategy used to resolve the path
     * @param tenantId    the tenant identifier, or {@code null} for untenanted namespaces
     * @param namespaceId the namespace identifier
     */
    public record Placement(Path dir, Layout layout, String tenantId, String namespaceId) {
        public Placement {
            Objects.requireNonNull(dir, "dir must not be null");
            Objects.requireNonNull(layout, "layout must not be null");
            Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        }
    }

    /**
     * Resolves the placement for a namespace.
     *
     * <p>If {@code tenantId == null}, resolves under {@link Layout#FLAT_SHA256} via
     * {@link StoragePaths#namespaceDirSharded(Path, String)}.
     * If {@code tenantId != null}, resolves under {@link Layout#TENANT_SHA256} via
     * {@link StoragePaths#tenantRootedNamespaceDir(Path, String, String)}.</p>
     *
     * <p>Both identifiers are strictly validated. No sentinel value is permitted for "no tenant".</p>
     *
     * @param root        the root persistence directory
     * @param tenantId    the tenant identifier, or {@code null} if untenanted
     * @param namespaceId the namespace identifier
     * @return the resolved {@link Placement}
     * @throws SpectorValidationException if either identifier is invalid or fails security checks
     */
    public static Placement resolve(Path root, String tenantId, String namespaceId) {
        Objects.requireNonNull(root, "root path must not be null");
        StoragePaths.validateNamespaceId(namespaceId);
        if (tenantId == null) {
            return new Placement(
                    StoragePaths.namespaceDirSharded(root, namespaceId),
                    Layout.FLAT_SHA256,
                    null,
                    namespaceId
            );
        }
        StoragePaths.validateTenantId(tenantId);
        return new Placement(
                StoragePaths.tenantRootedNamespaceDir(root, tenantId, namespaceId),
                Layout.TENANT_SHA256,
                tenantId,
                namespaceId
        );
    }

    /**
     * Derives the root directory prefix for a tenant's rememberers (Req R9.1, R9.4).
     *
     * <p>This path contains every rememberer belonging to {@code tenantId} and no other
     * tenant's data, enabling a full tenant rememberer wipe via a single prefix delete.
     * It is derivable from {@code tenantId} alone with no catalog dependency.</p>
     *
     * @param root     the root persistence directory
     * @param tenantId the tenant identifier
     * @return the tenant rememberer prefix path: {@code root/tenants/XX/YY/tenantId}
     * @throws SpectorValidationException if {@code tenantId} is invalid
     */
    public static Path tenantPrefix(Path root, String tenantId) {
        Objects.requireNonNull(root, "root path must not be null");
        StoragePaths.validateTenantId(tenantId);
        Path tenantDir = StoragePaths.shard2(root.resolve(StoragePaths.DIR_TENANTS), tenantId).resolve(tenantId);
        return StoragePaths.safeResolve(root, tenantDir);
    }
}
