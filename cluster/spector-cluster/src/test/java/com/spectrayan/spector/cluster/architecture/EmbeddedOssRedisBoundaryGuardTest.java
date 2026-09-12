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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural guard ensuring embedded and OSS users never take a Redis dependency (Req R11.2, ADR §15.6).
 *
 * <p>Scans all source and POM files under {@code memory/} and {@code nucleus/} to assert zero dependencies
 * or code references to Lettuce, Jedis, Redisson, or Spring Data Redis.</p>
 */
class EmbeddedOssRedisBoundaryGuardTest {

    private static final List<String> FORBIDDEN_REDIS_PATTERNS = List.of(
            "io.lettuce",
            "lettuce-core",
            "redis.clients",
            "org.redisson",
            "spring-boot-starter-data-redis",
            "spring-data-redis"
    );

    @Test
    @DisplayName("Req R11.2: No memory or nucleus module references Redis clients or drivers")
    void memoryAndNucleusMustNotReferenceRedis() throws IOException {
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
                .withFailMessage("Violations of Req R11.2 (embedded/OSS classpath must be Redis-free):\n"
                        + String.join("\n", violations))
                        .isEmpty();
    }

    @Test
    @DisplayName("Req R12.8: Guard detects planted Redis violation")
    void guardMustCatchPlantedRedisViolation(@TempDir Path tempDir) throws IOException {
        Path fakeSource = tempDir.resolve("FakeMemoryClass.java");
        Files.writeString(fakeSource, "import io.lettuce.core.RedisClient;\n");

        List<String> violations = new ArrayList<>();
        checkFile(fakeSource, tempDir, violations);

        assertThat(violations)
                .hasSize(1)
                .anyMatch(v -> v.contains("FakeMemoryClass.java:1") && v.contains("io.lettuce"));
    }

    private void checkFile(Path file, Path repoRoot, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (String pattern : FORBIDDEN_REDIS_PATTERNS) {
                    if (line.contains(pattern)) {
                        violations.add(repoRoot.relativize(file) + ":" + (i + 1) + ": " + line.trim());
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
