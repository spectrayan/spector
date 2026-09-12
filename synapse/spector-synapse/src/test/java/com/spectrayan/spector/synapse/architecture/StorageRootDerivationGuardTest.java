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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards how storage roots are derived (Task 2.10, Req R3.1, R3.4).
 *
 * <h3>Why these particular rules</h3>
 * <p>Four components once each derived the rememberer root by hand and disagreed: the resolver read
 * {@code persistence-path}, the migrator and startup detector read {@code data-dir}, and the CLI
 * defaulted to {@code ~/.spector/data}. Since the shipped {@code application.yml} points
 * {@code persistence-path} at {@code ${SPECTOR_DATA_DIR}/cognitive}, the migrator scanned a tree that
 * held no namespaces and reported success having moved nothing.</p>
 *
 * <p>The original version of this guard matched only literal
 * {@code .resolve("cognitive").resolve("namespaces")} constructions — a shape nobody had written —
 * so it passed against every one of those real violations. The rules below are written to fail on
 * what actually went wrong.</p>
 *
 * <p>Note what is deliberately <em>not</em> flagged: {@code McpCommand}, {@code InitCommand}, and the
 * host-integration wiring legitimately build paths from {@code user.home} in order to <em>set</em>
 * {@code persistence-path}. Configuring the property is fine; bypassing it is not.</p>
 */
@DisplayName("Req R3.1 / R3.4: Storage roots come from one accessor, not hand-rolled derivations")
class StorageRootDerivationGuardTest {

    /** Module source roots, relative to this module's directory. */
    private static final List<String> SCAN_ROOTS = List.of(
            "src/main/java",
            "../spector-cli/src/main/java",
            "../spector-mcp/src/main/java",
            "../spector-spring/src/main/java",
            "../../nucleus/spector-config/src/main/java",
            "../../memory/spector-kernel/src/main/java",
            "../../memory/spector-memory/src/main/java"
    );

    /**
     * The only class permitted to define how the rememberer and identity roots are computed.
     */
    private static final String ROOT_DEFINITION_CLASS = "SynapseProperties.java";

    /**
     * Classes permitted to declare the default data-dir literal, because declaring a default is
     * exactly their job.
     */
    private static final Set<String> DEFAULT_DECLARATION_CLASSES = Set.of(
            "SynapseProperties.java",
            "SpectorPropertyConstants.java"
    );

    private record SourceLine(Path file, int number, String text) {
        @Override
        public String toString() {
            return file + ":" + number + ": " + text.trim();
        }
    }

    /**
     * Streams non-comment source lines. Crude but sufficient: it drops whole-line comments and
     * javadoc, which is where every false positive in these rules would otherwise come from.
     */
    private static List<SourceLine> sourceLines() throws IOException {
        List<SourceLine> out = new ArrayList<>();
        for (String rootName : SCAN_ROOTS) {
            Path root = Paths.get(rootName);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                List<Path> files = stream.filter(p -> p.toString().endsWith(".java")).toList();
                for (Path file : files) {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String trimmed = lines.get(i).trim();
                        if (trimmed.startsWith("//") || trimmed.startsWith("*")
                                || trimmed.startsWith("/*") || trimmed.isEmpty()) {
                            continue;
                        }
                        out.add(new SourceLine(file, i + 1, lines.get(i)));
                    }
                }
            }
        }
        assertThat(out).as("the scanner must actually find source to inspect").isNotEmpty();
        return out;
    }

    private static String fileName(Path p) {
        return p.getFileName().toString();
    }

    @Test
    @DisplayName("Only SynapseProperties may derive a root by pairing persistence-path with a data-dir fallback")
    void noHandRolledRootDerivation() throws IOException {
        Pattern persistencePath = Pattern.compile("getPersistencePath\\s*\\(\\s*\\)");
        Pattern dataDir = Pattern.compile("\\bdataDir\\s*\\(\\s*\\)|getDataDir\\s*\\(\\s*\\)");

        // Group offending lines by file: the smell is one class containing both halves of the
        // "persistence-path, else data-dir" idiom, which is the copy-paste that caused the drift.
        List<Path> offenders = new ArrayList<>();
        List<SourceLine> lines = sourceLines();
        for (Path file : lines.stream().map(SourceLine::file).distinct().toList()) {
            if (ROOT_DEFINITION_CLASS.equals(fileName(file))) {
                continue;
            }
            List<SourceLine> fileLines = lines.stream().filter(l -> l.file().equals(file)).toList();
            boolean hasPersistence = fileLines.stream().anyMatch(l -> persistencePath.matcher(l.text()).find());
            boolean hasDataDir = fileLines.stream().anyMatch(l -> dataDir.matcher(l.text()).find());
            if (hasPersistence && hasDataDir) {
                offenders.add(file);
            }
        }

        assertThat(offenders)
                .as("These classes re-derive a storage root instead of calling "
                        + "SynapseProperties.remembererRoot() / identityRoot(). That duplication is how the "
                        + "resolver, migrator, detector, and CLI drifted onto four different roots (Req R3.1).")
                .isEmpty();
    }

    @Test
    @DisplayName("The default data-dir literal appears only where a default is declared")
    void defaultDataDirLiteralIsNotDuplicated() throws IOException {
        Pattern literal = Pattern.compile("\"\\./spector-data\"");

        List<SourceLine> offenders = sourceLines().stream()
                .filter(l -> !DEFAULT_DECLARATION_CLASSES.contains(fileName(l.file())))
                .filter(l -> literal.matcher(l.text()).find())
                .toList();

        assertThat(offenders)
                .as("Use SynapseProperties.DEFAULT_DATA_DIR instead of repeating the literal. It was "
                        + "previously inlined as a fallback in the resolver, the migrator, and the detector, "
                        + "each of which could then disagree about the root (Req R3.4).")
                .isEmpty();
    }

    @Test
    @DisplayName("Migration components resolve the rememberer root, never data-dir")
    void migrationComponentsDoNotUseDataDir() throws IOException {
        Pattern dataDir = Pattern.compile("\\bdataDir\\s*\\(\\s*\\)|getDataDir\\s*\\(\\s*\\)");

        List<SourceLine> offenders = sourceLines().stream()
                .filter(l -> l.file().toString().contains("/migration/"))
                .filter(l -> dataDir.matcher(l.text()).find())
                .toList();

        assertThat(offenders)
                .as("The migrator and startup detector operate on the tree NamespaceResolver opens, which "
                        + "is SynapseProperties.remembererRoot(). Reading data-dir here made migration a "
                        + "silent no-op and made the readiness check always pass (Req R3.1).")
                .isEmpty();
    }

    @Test
    @DisplayName("No hardcoded 'cognitive' or 'memory' leaf is used to build a rememberer path")
    void noHardcodedRemembererRootLeaves() throws IOException {
        Pattern hardcodedLeaf = Pattern.compile(
                "\\.resolve\\(\\s*\"(cognitive|memory)[/\\\\](namespaces|tenants)\"\\s*\\)|"
                        + "\\.resolve\\(\\s*\"(cognitive|memory)\"\\s*\\)\\s*\\.resolve\\(\\s*\"(namespaces|tenants)\"\\s*\\)");

        List<SourceLine> offenders = sourceLines().stream()
                .filter(l -> hardcodedLeaf.matcher(l.text()).find())
                .toList();

        assertThat(offenders)
                .as("The rememberer root's leaf differs by deployment — 'cognitive' under Synapse's "
                        + "application.yml, 'memory' under the framework default — so neither may be spelled "
                        + "in a path literal. Derive it from remembererRoot() (D1, Req R3.4).")
                .isEmpty();
    }
}
