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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the live rememberer path and roots (Tasks 0.2 and 0.3, Spec §1.1, §1.3, Req R1.2, R3.2, R11.1).
 *
 * <p>Ensures that:</p>
 * <ul>
 *   <li>The untenanted data-plane path resolves strictly to
 *       {@code {persistence-path}/namespaces/{sha[0:2]}/{sha[2:4]}/{namespaceId}/} (Task 0.2);</li>
 *   <li>The framework default rememberer root ends with {@code .spector/memory} (Task 0.3a);</li>
 *   <li>The shipped {@code application.yml} configures {@code persistence-path} with leaf {@code cognitive}
 *       and {@code data-dir} as {@code ${SPECTOR_DATA_DIR:./spector-data}} (Task 0.3b).</li>
 * </ul>
 */
@DisplayName("Tasks 0.2 & 0.3: Pin live path and configuration roots")
class NamespacePathPinTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Task 0.2: Pin live untenanted path format to StoragePaths.namespaceDirSharded")
    void testPinLiveUntenantedPath() throws Exception {
        SynapseProperties props = new SynapseProperties();
        Path basePath = tempDir.resolve("cognitive");
        props.getMemory().setPersistencePath(basePath.toString());

        AccountCatalog catalog = mock(AccountCatalog.class);
        NamespaceResolver resolver = new NamespaceResolver(
                catalog, props, null, null, null, null, null, null, null, null, null, 10
        );

        Method basePathMethod = NamespaceResolver.class.getDeclaredMethod("basePath");
        basePathMethod.setAccessible(true);
        Path resolvedBasePath = (Path) basePathMethod.invoke(resolver);
        assertThat(resolvedBasePath).isEqualTo(basePath);

        String namespaceId = "0195500000001";
        Path expectedPath = StoragePaths.namespaceDirSharded(basePath, namespaceId);

        String hash = StoragePaths.sha256Hex(namespaceId);
        String l1 = hash.substring(0, 2);
        String l2 = hash.substring(2, 4);
        Path manualExpected = basePath.resolve("namespaces").resolve(l1).resolve(l2).resolve(namespaceId);

        assertThat(expectedPath)
                .as("Live untenanted path must match SHA-256 two-level sharded directory")
                .isEqualTo(manualExpected);
    }

    @Test
    @DisplayName("Task 0.3a: Pin framework default rememberer root to .spector/memory")
    void testPinFrameworkDefaultRoot() {
        Path defaultFrameworkPath = SpectorPropertyConstants.DEFAULT_MEMORY_PERSISTENCE_PATH;
        assertThat(defaultFrameworkPath).isEqualTo(Path.of(".spector", "memory"));
        assertThat(defaultFrameworkPath.getFileName().toString())
                .as("Framework default rememberer leaf name must be 'memory'")
                .isEqualTo("memory");

        MemoryProperties defaultMemProps = new MemoryProperties();
        Path configuredPath = Path.of(defaultMemProps.getPersistencePath());
        assertThat(configuredPath.endsWith(Path.of(".spector", "memory")))
                .as("Default MemoryProperties persistence-path must end with .spector/memory")
                .isTrue();
    }

    @Test
    @DisplayName("Task 0.3b: Pin shipped application.yml persistence-path (leaf cognitive) and data-dir")
    @SuppressWarnings("unchecked")
    void testPinShippedApplicationYmlRoots() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(in).as("application.yml must be present on classpath").isNotNull();
            Map<String, Object> root = yaml.load(in);
            assertThat(root).containsKey("spector");

            Map<String, Object> spector = (Map<String, Object>) root.get("spector");
            String dataDir = (String) spector.get("data-dir");
            assertThat(dataDir)
                    .as("spector.data-dir in application.yml must match ${SPECTOR_DATA_DIR:./spector-data}")
                    .isEqualTo("${SPECTOR_DATA_DIR:./spector-data}");

            Map<String, Object> memory = (Map<String, Object>) spector.get("memory");
            assertThat(memory).isNotNull();

            String persistencePath = (String) memory.get("persistence-path");
            assertThat(persistencePath)
                    .as("spector.memory.persistence-path in application.yml must be ${SPECTOR_DATA_DIR:./spector-data}/cognitive")
                    .isEqualTo("${SPECTOR_DATA_DIR:./spector-data}/cognitive");

            assertThat(persistencePath)
                    .as("Shipped application.yml rememberer leaf must end with /cognitive")
                    .endsWith("/cognitive");
        } catch (Exception e) {
            throw new RuntimeException("Failed to read application.yml", e);
        }
    }

    /**
     * Reads a {@code static final boolean} reflectively.
     *
     * <p>javac inlines compile-time constants into the calling class, so referencing the field
     * directly would assert whatever value was on the classpath when <em>this test</em> was compiled,
     * not the value shipping in {@code spector-config}. A pin test whose entire purpose is to catch a
     * regression in that constant must not be able to pass against a stale copy of it.</p>
     */
    private static boolean constantBoolean(String fieldName) throws Exception {
        java.lang.reflect.Field f = SpectorPropertyConstants.class.getField(fieldName);
        return (boolean) f.get(null);
    }

    @Test
    @DisplayName("Task 3.1: Pin tenant-rooted flag default to OFF so upgrades are bit-identical (Req R11.1)")
    void testPinTenantRootedFlagDefaultsOff() throws Exception {
        assertThat(constantBoolean("DEFAULT_NAMESPACE_TENANT_ROOTED_ENABLED"))
                .as("The tenant-rooted layout must default to OFF. Defaulting it ON flips existing installs "
                        + "onto layout B with no migration run, which is exactly what the staged rollout "
                        + "in spec design §5 exists to prevent (Req R11.1).")
                .isFalse();

        assertThat(new SynapseProperties().getNamespace().isTenantRootedEnabled())
                .as("A freshly constructed SynapseProperties must report the tenant-rooted layout as disabled")
                .isFalse();
    }

    @Test
    @DisplayName("Task 4.4: Pin dual-read fallback default to ON so a premature flag flip degrades safely (Req R5.3)")
    void testPinDualReadDefaultsOn() throws Exception {
        assertThat(constantBoolean("DEFAULT_NAMESPACE_DUAL_READ_ENABLED"))
                .as("Dual-read must default ON: an operator who enables the tenant-rooted layout before "
                        + "migrating should get a fallback read, not a silently empty namespace.")
                .isTrue();

        assertThat(new SynapseProperties().getNamespace().isDualReadEnabled())
                .as("A freshly constructed SynapseProperties must report dual-read as enabled")
                .isTrue();
    }
}
