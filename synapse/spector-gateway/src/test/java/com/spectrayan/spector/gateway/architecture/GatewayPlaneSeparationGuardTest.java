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
package com.spectrayan.spector.gateway.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Gateway Plane Separation Guard Test (Invariant T4, ADR-0081 §8 Phase 2)")
class GatewayPlaneSeparationGuardTest {

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "spector-memory",
            "spector-kernel",
            "spector-gpu",
            "spector-index",
            "spector-connector",
            "spector-batch",
            "spector-ingestion",
            "spector-mcp",
            "org.apache.catalina",
            "org.apache.tomcat",
            "org.flywaydb",
            "org.h2",
            "org.apache.camel",
            "org.quartz",
            "jdk.incubator.vector"
    );

    @Test
    @DisplayName("Invariant T4: spector-gateway source and pom have zero forbidden dependencies")
    void gatewayMustNotDependOnDataPlane() throws IOException {
        Path repoRoot = findRepoRoot();
        Path gatewayModule = repoRoot.resolve("synapse/spector-gateway");

        assertThat(gatewayModule).exists();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(gatewayModule)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith("pom.xml"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().contains("GatewayPlaneSeparationGuardTest.java"))
                    .forEach(p -> checkFile(p, repoRoot, violations));
        }

        assertThat(violations)
                .withFailMessage("Violations of Invariant T4 (Gateway plane separation):\n"
                        + String.join("\n", violations))
                .isEmpty();
    }

    private void checkFile(Path file, Path repoRoot, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (String forbidden : FORBIDDEN_TOKENS) {
                    if (line.contains(forbidden)) {
                        violations.add(repoRoot.relativize(file) + ":" + (i + 1) + ": forbidden token '" + forbidden + "' in: " + line.trim());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed reading " + file, e);
        }
    }

    private Path findRepoRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(".git"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not find repository root (.git)");
        }
        return current;
    }
}
