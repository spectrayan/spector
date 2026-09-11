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
package com.spectrayan.spector.synapse.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural guardrail enforcing that no production class outside the sanctioned resolver
 * references raw namespace-path helpers directly (Tasks 0.5 and 4.6, Req R6.5, Invariant I2).
 *
 * <p>Sanctioned choke point: {@code NamespacePathResolver} (and internal {@code StoragePaths} definitions).
 * All other production callers must resolve rememberer paths via {@code NamespacePathResolver}.</p>
 *
 * <p>Uses file-tree parsing rather than ArchUnit because ArchUnit 1.4.0 silently ignores major version 69
 * (Java 25) classes (see issue #734).</p>
 */
@DisplayName("Task 0.5 / 4.6: No raw namespace-path helper calls outside sanctioned resolver")
class NoRawPathHelperGuardTest {

    private static final Set<String> ALLOWED_FILES = Set.of(
            "StoragePaths.java",
            "NamespacePathResolver.java",
            "LayoutMigrator.java",
            "SpectorNamespaceManager.java",
            "TenantNamespaceMigrator.java"
    );

    private static final Pattern RAW_PATH_HELPER_CALL = Pattern.compile(
            "\\bStoragePaths\\.(namespaceDirSharded|namespaceDir|tenantNamespaceDirSharded)\\s*\\("
    );

    @Test
    @Disabled("Task 0.5: Documents current violations across FileAccountCatalog, NamespaceResolver, etc. Enabled in Task 4.6 after Group 3 and Group 4 eliminate all unsanctioned raw path helper call sites.")
    @DisplayName("Verify no production class invokes raw path helpers outside allowlist")
    void noProductionClassCallsRawPathHelpers() throws IOException {
        List<Path> scanRoots = List.of(
                Paths.get("src/main/java"),
                Paths.get("../spector-memory/src/main/java"),
                Paths.get("../../memory/spector-memory/src/main/java"),
                Paths.get("../../memory/spector-kernel/src/main/java")
        );

        List<String> violations = new ArrayList<>();

        for (Path root : scanRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                        .forEach(file -> {
                            String fileName = file.getFileName().toString();
                            if (ALLOWED_FILES.contains(fileName)) {
                                return;
                            }
                            try {
                                List<String> lines = Files.readAllLines(file);
                                for (int i = 0; i < lines.size(); i++) {
                                    String line = lines.get(i);
                                    if (RAW_PATH_HELPER_CALL.matcher(line).find()) {
                                        violations.add(file + ":" + (i + 1) + ": " + line.trim());
                                    }
                                }
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to scan " + file, e);
                            }
                        });
            }
        }

        assertThat(violations)
                .as("Found unsanctioned raw namespace path helper calls outside allowlist")
                .isEmpty();
    }
}
