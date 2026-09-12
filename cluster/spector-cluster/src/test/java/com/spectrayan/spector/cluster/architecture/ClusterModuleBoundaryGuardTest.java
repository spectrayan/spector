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
package com.spectrayan.spector.cluster.architecture;

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

/**
 * Architectural guard ensuring the memory kernel and nucleus remain cell-unaware (Req R1.4, ADR §15.1).
 *
 * <p>Scans all source and POM files under {@code memory/} and {@code nucleus/} to assert zero dependencies
 * or code references to {@code spector-cluster} or {@code com.spectrayan.spector.cluster}.</p>
 */
class ClusterModuleBoundaryGuardTest {

    @Test
    @DisplayName("Req R1.4: No memory or nucleus module references spector-cluster")
    void memoryAndNucleusMustNotReferenceClusterModule() throws IOException {
        Path repoRoot = findRepoRoot();
        List<Path> scanRoots = List.of(
                repoRoot.resolve("memory"),
                repoRoot.resolve("nucleus")
        );

        List<String> violations = new ArrayList<>();

        for (Path scanRoot : scanRoots) {
            if (!Files.exists(scanRoot)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(scanRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith("pom.xml"))
                        .filter(p -> !p.toString().contains("/target/"))
                        .forEach(p -> checkFile(p, repoRoot, violations));
            }
        }

        assertThat(violations)
                .withFailMessage("Violations of Req R1.4 (kernel must remain cell-unaware):\n"
                        + String.join("\n", violations))
                .isEmpty();
    }

    @Test
    @DisplayName("Req R12.8: Guard detects planted violation")
    void guardMustCatchPlantedViolation(@org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        Path fakeSource = tempDir.resolve("FakeKernelClass.java");
        Files.writeString(fakeSource, "import com.spectrayan.spector.cluster.routing.RoutingKey;\n");

        List<String> violations = new ArrayList<>();
        checkFile(fakeSource, tempDir, violations);

        assertThat(violations)
                .hasSize(1)
                .anyMatch(v -> v.contains("FakeKernelClass.java:1") && v.contains("com.spectrayan.spector.cluster"));
    }

    private void checkFile(Path file, Path repoRoot, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("spector-cluster") || line.contains("com.spectrayan.spector.cluster")) {
                    violations.add(repoRoot.relativize(file) + ":" + (i + 1) + ": " + line.trim());
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
