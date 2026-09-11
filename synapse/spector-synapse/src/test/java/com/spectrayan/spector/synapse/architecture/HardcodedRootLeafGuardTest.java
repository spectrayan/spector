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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural guardrail enforcing that no production class or test in this spec's scope
 * contains a "cognitive" or "memory" path-segment literal for the rememberer root —
 * everything must derive from {@code basePath()} (Task 2.10, Req R3.4).
 */
@DisplayName("Task 2.10: Guard against hardcoded root leaves in rememberer resolution")
class HardcodedRootLeafGuardTest {

    // Matches attempts to resolve a hardcoded "cognitive" or "memory" root directory for rememberers
    // e.g. .resolve("cognitive").resolve("namespaces") or .resolve("memory").resolve("namespaces")
    // or .resolve("cognitive/namespaces") or .resolve("memory/namespaces")
    // or .resolve("cognitive").resolve("tenants") or .resolve("memory").resolve("tenants")
    private static final Pattern HARDCODED_ROOT_LEAF = Pattern.compile(
            "\\.resolve\\(\\s*\"(cognitive|memory)[/\\\\](namespaces|tenants)\"\\s*\\)|" +
            "\\.resolve\\(\\s*\"(cognitive|memory)\"\\s*\\)\\.resolve\\(\\s*\"(namespaces|tenants)\"\\s*\\)"
    );

    @Test
    @DisplayName("Verify no production class or test hardcodes 'cognitive' or 'memory' root leaves for rememberers")
    void noHardcodedRemembererRootLeaves() throws IOException {
        List<Path> scanRoots = List.of(
                Paths.get("src/main/java"),
                Paths.get("src/test/java"),
                Paths.get("../../memory/spector-memory/src/main/java"),
                Paths.get("../../memory/spector-memory/src/test/java"),
                Paths.get("../../memory/spector-kernel/src/main/java"),
                Paths.get("../../memory/spector-kernel/src/test/java")
        );

        List<String> violations = new ArrayList<>();

        for (Path root : scanRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.getFileName().toString().equals("HardcodedRootLeafGuardTest.java"))
                        .forEach(file -> {
                            try {
                                List<String> lines = Files.readAllLines(file);
                                for (int i = 0; i < lines.size(); i++) {
                                    String line = lines.get(i).trim();
                                    if (line.startsWith("//") || line.startsWith("*")) {
                                        continue;
                                    }
                                    if (HARDCODED_ROOT_LEAF.matcher(line).find()) {
                                        violations.add(file + ":" + (i + 1) + ": " + line);
                                    }
                                }
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to scan " + file, e);
                            }
                        });
            }
        }

        assertThat(violations)
                .as("Found hardcoded 'cognitive' or 'memory' root leaf literals for rememberer resolution (Req R3.4)")
                .isEmpty();
    }
}
