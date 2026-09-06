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
package com.spectrayan.spector.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guardrail test enforcing ADR-0031.
 *
 * <p>Ensures that post-bootstrap production code never bypasses {@link SpectorProperties}
 * or {@link SpectorConfigFactory} by directly querying {@code System.getProperty("spector.*")},
 * {@code Long.getLong("spector.*")}, {@code Integer.getInteger("spector.*")},
 * {@code Boolean.getBoolean("spector.*")}, or raw {@code System.getenv("SPECTOR_*")}.</p>
 *
 * <p>Only {@link SpectorConfigSource} is authorized to read bootstrap properties from JVM
 * system properties or environment variables during initial snapshot creation.</p>
 */
@DisplayName("PostBootstrapSyspropBanTest: Architectural Guardrails")
class PostBootstrapSyspropBanTest {

    private static final Pattern SYSPROP_BAN_PATTERN = Pattern.compile(
            "\\b(System\\.getProperty|Long\\.getLong|Integer\\.getInteger|Boolean\\.getBoolean)\\s*\\(\\s*\"(spector\\.[^\"]+)\""
    );

    private static final Pattern ENV_BAN_PATTERN = Pattern.compile(
            "\\bSystem\\.getenv(?:\\(\\)\\.get(?:OrDefault)?)?\\s*\\(\\s*\"(SPECTOR_[^\"]+)\""
    );

    private static final Pattern BENCH_CORE_PROPS_BAN_PATTERN = Pattern.compile(
            "\"spector\\.(memory|provider|recall|concurrency|hardware|events|telemetry|circadian|dream|twofactor)\\."
    );

    @Test
    @DisplayName("Production sources outside SpectorConfigSource must not query spector.* system properties or SPECTOR_* env vars")
    void testNoPostBootstrapSystemPropertyBypasses() throws IOException {
        Path repoRoot = findRepoRoot();
        assertThat(repoRoot).as("Repository root containing pom.xml and nucleus/ must exist").isNotNull();

        List<Path> productionFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .forEach(productionFiles::add);
        }

        assertThat(productionFiles)
                .as("Should find production Java files across reactor modules")
                .hasSizeGreaterThan(200);

        List<String> violations = new ArrayList<>();

        for (Path file : productionFiles) {
            String normPath = file.toString().replace('\\', '/');

            // SpectorConfigSource is the sole authorized bootstrap reader
            if (file.getFileName().toString().equals("SpectorConfigSource.java")) {
                continue;
            }

            boolean isBench = normPath.contains("/bench/") || normPath.contains("/spector-bench/");

            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int lineNum = i + 1;

                if (isBench) {
                    // Bench harnesses may use spector.bench.* or spector.benchmark.* CLI flags,
                    // but must never bypass core memory/provider/recall config via spector.memory.*, etc.
                    Matcher benchMatcher = BENCH_CORE_PROPS_BAN_PATTERN.matcher(line);
                    if (benchMatcher.find() && (line.contains("System.getProperty") || line.contains("Long.getLong")
                            || line.contains("Integer.getInteger") || line.contains("Boolean.getBoolean"))) {
                        violations.add(String.format("%s:%d -> %s", relPath(repoRoot, file), lineNum, line.trim()));
                    }
                } else {
                    // All other production modules are strictly forbidden from reading spector.* sysprops or SPECTOR_* envs
                    Matcher syspropMatcher = SYSPROP_BAN_PATTERN.matcher(line);
                    if (syspropMatcher.find()) {
                        violations.add(String.format("%s:%d [Sysprop %s] -> %s",
                                relPath(repoRoot, file), lineNum, syspropMatcher.group(2), line.trim()));
                    }

                    Matcher envMatcher = ENV_BAN_PATTERN.matcher(line);
                    if (envMatcher.find()) {
                        violations.add(String.format("%s:%d [Env %s] -> %s",
                                relPath(repoRoot, file), lineNum, envMatcher.group(1), line.trim()));
                    }
                }
            }
        }

        assertThat(violations)
                .as("Found post-bootstrap system property or environment variable bypasses violating ADR-0031:\n"
                        + String.join("\n", violations))
                .isEmpty();
    }

    private static Path findRepoRoot() {
        Path cur = Path.of("").toAbsolutePath();
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml"))
                    && Files.exists(cur.resolve("nucleus"))
                    && Files.exists(cur.resolve("memory"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return Path.of("").toAbsolutePath();
    }

    private static String relPath(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.toString().replace('\\', '/');
        }
    }
}
