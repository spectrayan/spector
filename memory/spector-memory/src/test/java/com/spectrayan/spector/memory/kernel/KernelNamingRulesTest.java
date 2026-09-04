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
package com.spectrayan.spector.memory.kernel;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the kernel naming rules from the {@code engram-layout-unification} spec (design §2.1).
 *
 * <h3>Every rule here is currently {@link Disabled} and expected to FAIL</h3>
 *
 * <p>These rules describe the <em>target</em> vocabulary, not the current one. They are committed
 * up-front, disabled, so each rename task has an objective completion signal: enable the rule, watch
 * it go green, and it can never regress afterwards. Do not weaken a rule to make it pass.</p>
 *
 * <table border="1">
 *   <caption>Rule to spec task mapping</caption>
 *   <tr><th>Rule</th><th>Enabled by</th><th>Blocking rename</th></tr>
 *   <tr><td>{@link #noSizeOrVersionInKernelTypeNames()}</td><td>task 3.4</td>
 *       <td>{@code HeaderLayout64}, {@code HeaderLayout64V2} → {@code EncodingHeaderLayout}</td></tr>
 *   <tr><td>{@link #noKernelTypeShadowsPanama()}</td><td>task 1.2</td>
 *       <td>{@code kernel.MemoryLayout} → {@code kernel.RegionLayout}</td></tr>
 *   <tr><td>{@link #shapeTokensOnlyInShapePackage()}</td><td>tasks 2.2, 2.3, 2b.1–2b.4</td>
 *       <td>{@code AuditRecordLayout} → {@code StrengthLayout},
 *           {@code SemanticRecordMemory} → {@code SemanticMemory}</td></tr>
 *   <tr><td>{@link #layoutSuffixMeansRegionLayout()}</td><td>task 1.4</td>
 *       <td>{@code AdjacencyListLayout}, {@code CoActivationMetadataLayout} → {@code *Fields}</td></tr>
 * </table>
 *
 * <h3>Why this does not use ArchUnit</h3>
 *
 * <p>ArchUnit 1.4.0 (the version already declared in {@code synapse/spector-mcp}) bundles an ASM that
 * cannot parse <b>class file major version 69</b>, which is what Java 25 emits. It does not throw — it
 * silently imports <b>zero</b> classes, so every rule passes vacuously. Three import strategies were
 * tried and all returned zero: {@code importPackages(...)} classpath scanning,
 * {@code getProtectionDomain().getCodeSource()}, and an explicit {@code importPath("target/classes")}
 * verified to contain 768 class files.</p>
 *
 * <p>These rules are name predicates plus one {@code implements} check, so they need no bytecode
 * library: class names come from the file tree and the one type check uses reflection. That also keeps
 * the module free of a test dependency it cannot currently use.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/732">#732</a>
 */
@DisplayName("Kernel naming rules (engram-layout-unification)")
class KernelNamingRulesTest {

    private static final String KERNEL_PKG = "com.spectrayan.spector.memory.kernel";
    private static final String SHAPE_PKG = KERNEL_PKG + ".shape";
    private static final String LAYOUT_PKG = KERNEL_PKG + ".layout";
    private static final String COMPAT_SEGMENT = ".compat.";

    /**
     * Simple names of Panama types that a kernel type must not shadow.
     *
     * @see java.lang.foreign.MemoryLayout
     */
    private static final Set<String> PANAMA_TYPES =
            Set.of("MemoryLayout", "MemorySegment", "ValueLayout", "Arena");

    /**
     * Allowlisted exception to the shape-token rule.
     *
     * <p>Dropping {@code Record} would give {@code IndexMemory}, which collides conceptually with the
     * {@code MemoryIndex} interface that extends it. Settling which name owns the directory role is out
     * of scope for this spec — requirements §6 decision 6. Encoded here so the exception stays visible
     * rather than silent.</p>
     */
    private static final Set<String> SHAPE_TOKEN_ALLOWLIST = Set.of("IndexRecordMemory");

    /**
     * Names where a size-like suffix is part of an algorithm name, not a storage format version.
     *
     * <p>{@code XxHash64} is the xxHash64 algorithm; the {@code 64} denotes the hash width and carries
     * no format-version meaning, so rule 1 must not demand it be renamed.</p>
     */
    private static final Set<String> SIZE_SUFFIX_ALLOWLIST = Set.of("XxHash64");

    private static final Pattern SIZE_OR_VERSION_SUFFIX = Pattern.compile(".*(64|128|V\\d+)$");

    /**
     * Only {@code Record} and {@code Append} are shape tokens.
     *
     * <p>They describe <em>how a store is accessed</em> — fixed-stride slots versus an append cursor —
     * which is information already carried by {@code MemoryShape} and {@code kernel.shape.*}.</p>
     *
     * <p>{@code Graph}, {@code Chain} and {@code Registry} are deliberately <b>excluded</b>: those name
     * the <em>data structure being stored</em>, which is content. A Hebbian graph really is a graph, a
     * temporal chain really is a chain, and a type registry really is a registry — so
     * {@code HebbianGraphMemory}, {@code TemporalChainMemory}, {@code TypeRegistryMemory} and
     * {@code RegistryLayout} are correct as they stand and appear in the spec's "unchanged and correct"
     * list (design §4.3).</p>
     */
    private static final Pattern SHAPE_TOKEN_SUFFIX =
            Pattern.compile(".*(Record|Append)(Memory|Layout)$");

    private static final Path CLASSES_DIR = resolveClassesDir();
    private static final List<String> ALL_TYPES = discoverTypes();

    /** Surefire sets the working directory to the module basedir; the fallback covers reactor-root runners. */
    private static Path resolveClassesDir() {
        Path direct = Paths.get("target", "classes");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return Paths.get("memory", "spector-memory", "target", "classes");
    }

    /**
     * Fully-qualified names of every compiled production type, nested types included, derived from the
     * file tree rather than from bytecode.
     */
    private static List<String> discoverTypes() {
        if (!Files.isDirectory(CLASSES_DIR)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(CLASSES_DIR)) {
            return paths.filter(Files::isRegularFile)
                    .map(CLASSES_DIR::relativize)
                    .map(Path::toString)
                    .filter(p -> p.endsWith(".class"))
                    .filter(p -> !p.endsWith("module-info.class") && !p.endsWith("package-info.class"))
                    .map(p -> p.substring(0, p.length() - ".class".length()).replace(java.io.File.separatorChar, '.'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + CLASSES_DIR.toAbsolutePath(), e);
        }
    }

    /** Trailing identifier of a possibly-nested type: {@code a.b.Outer$Inner} to {@code Inner}. */
    private static String simpleName(String fqn) {
        int dollar = fqn.lastIndexOf('$');
        int dot = fqn.lastIndexOf('.');
        return fqn.substring(Math.max(dollar, dot) + 1);
    }

    private static String packageOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? "" : fqn.substring(0, dot);
    }

    /** Anonymous and synthetic types carry no author-chosen name, so naming rules cannot apply. */
    private static boolean isAuthorNamed(String fqn) {
        String simple = simpleName(fqn);
        return !simple.isEmpty() && !Character.isDigit(simple.charAt(0));
    }

    // ── Rule 1 ──

    /**
     * No byte width or version token in a live type name.
     *
     * <p>{@code HeaderLayout64} and {@code HeaderLayout64V2} are both 64 bytes, so {@code 64} does not
     * even discriminate between them. Format version belongs in the version byte at record offset 0 and
     * in {@code RegionEntry.schemaVersion}, never in an identifier. Read-only legacy decoders are
     * permitted, but must be named {@code Legacy*} inside a {@code compat} package so their status is
     * visible at the import site.</p>
     */
    @Test
    @Disabled("Enabled by spec task 3.4 — fails until HeaderLayout64/V2 collapse into EncodingHeaderLayout")
    @DisplayName("Rule 1: no size or version token in kernel type names")
    void noSizeOrVersionInKernelTypeNames() {
        List<String> violations = ALL_TYPES.stream()
                .filter(KernelNamingRulesTest::isAuthorNamed)
                .filter(fqn -> packageOf(fqn).startsWith(KERNEL_PKG))
                .filter(fqn -> !fqn.contains(COMPAT_SEGMENT))
                .filter(fqn -> !SIZE_SUFFIX_ALLOWLIST.contains(simpleName(fqn)))
                .filter(fqn -> SIZE_OR_VERSION_SUFFIX.matcher(simpleName(fqn)).matches())
                .toList();

        assertThat(violations)
                .as("format version belongs in the record's version byte and RegionEntry.schemaVersion, "
                        + "not in a type name; legacy decoders belong in a compat package named Legacy*")
                .isEmpty();
    }

    // ── Rule 2 ──

    /**
     * No kernel type may shadow a {@code java.lang.foreign} type.
     *
     * <p>{@code kernel.MemoryLayout} collides with Panama's {@link java.lang.foreign.MemoryLayout}, the
     * root of the FFM layout API. Over 20 files import both the Spector interface and
     * {@code java.lang.foreign.*}. Nothing is forced to fully-qualify today, so the collision is latent
     * — but in a Panama-first kernel, any future use of FFM group layouts inside {@code kernel.layout}
     * would require a fully-qualified name.</p>
     */
    @Test
    @DisplayName("Rule 2: no kernel type shadows a java.lang.foreign type")
    void noKernelTypeShadowsPanama() {
        List<String> violations = ALL_TYPES.stream()
                .filter(fqn -> packageOf(fqn).startsWith(KERNEL_PKG))
                .filter(fqn -> PANAMA_TYPES.contains(simpleName(fqn)))
                .toList();

        assertThat(violations)
                .as("shadowing a java.lang.foreign type forces fully-qualified names in a Panama-first "
                        + "kernel; use RegionLayout for the region record descriptor")
                .isEmpty();
    }

    // ── Rule 5 ──

    /**
     * Shape belongs on the shape abstraction, never on a concrete store or layout.
     *
     * <p>{@code MemoryShape} and {@code kernel.shape.*} legitimately carry {@code Record},
     * {@code Append}, {@code Graph}, {@code Chain} and {@code Registry} — that is what those types
     * <em>are</em>. A concrete store or layout names its content instead, so {@code AbstractRecordMemory}
     * is correct while {@code SemanticRecordMemory} is not.</p>
     *
     * <p>When the spec was written only 3 of 18 region-layout implementors carried {@code Record}
     * ({@code AuditRecordLayout}, {@code CognitiveRecordLayout}, {@code WalRecordLayout}), so dropping it
     * conforms the outliers to the existing 15 rather than inventing a convention.</p>
     */
    @Test
    @Disabled("Enabled by spec tasks 2.2, 2.3 and 2b.1-2b.4 — fails until shape tokens are dropped")
    @DisplayName("Rule 5: shape tokens only inside kernel.shape")
    void shapeTokensOnlyInShapePackage() {
        List<String> violations = ALL_TYPES.stream()
                .filter(KernelNamingRulesTest::isAuthorNamed)
                .filter(fqn -> !packageOf(fqn).startsWith(SHAPE_PKG))
                .filter(fqn -> !SHAPE_TOKEN_ALLOWLIST.contains(simpleName(fqn)))
                .filter(fqn -> SHAPE_TOKEN_SUFFIX.matcher(simpleName(fqn)).matches())
                .toList();

        assertThat(violations)
                .as("shape is already carried by MemoryShape and kernel.shape.*; a concrete store or "
                        + "layout names its content only (requirements R3.3). Graph/Chain/Registry are "
                        + "content, not shape, so they are not flagged. Allowlisted: %s",
                        SHAPE_TOKEN_ALLOWLIST)
                .isEmpty();
    }

    // ── Rule 6 ──

    /**
     * A {@code *Layout} type in {@code kernel.layout} must actually be a region layout.
     *
     * <p>{@code AdjacencyListLayout} and {@code CoActivationMetadataLayout} carry the suffix but do not
     * implement the contract — both are plain constants classes. Constants-only helpers belong under
     * {@code *Fields}, address computation under {@code *Accessor}, variable-length framing under
     * {@code *Codec}.</p>
     *
     * <p>Scoped to {@code kernel.layout}: {@code BundleLayout} lives in {@code kernel.bundle}, and
     * {@code StorageLayout} in {@code kernel} is a third, unrelated sense of "layout" (filesystem
     * paths) that is deliberately out of scope.</p>
     */
    @Test
    @DisplayName("Rule 6: a *Layout type in kernel.layout implements the region-layout contract")
    void layoutSuffixMeansRegionLayout() {
        List<String> violations = ALL_TYPES.stream()
                .filter(fqn -> packageOf(fqn).equals(LAYOUT_PKG))
                .filter(fqn -> simpleName(fqn).endsWith("Layout"))
                .filter(fqn -> {
                    Class<?> type = load(fqn);
                    // A type that cannot be loaded is reported rather than silently passed.
                    return type == null || (!type.isInterface() && !RegionLayout.class.isAssignableFrom(type));
                })
                .toList();

        assertThat(violations)
                .as("the Layout suffix must mean 'region record descriptor'; constants-only helpers "
                        + "belong under *Fields, addressing under *Accessor, framing under *Codec")
                .isEmpty();
    }

    private static Class<?> load(String fqn) {
        try {
            return Class.forName(fqn, false, KernelNamingRulesTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    // ── Guard ──

    /**
     * Proves the type discovery actually found classes.
     *
     * <p>This is the guard that caught ArchUnit importing zero classes. Without it, all four rules
     * above would pass vacuously the moment they were enabled, and the spec's rename tasks would get a
     * false green signal. It is deliberately <b>not</b> {@code @Disabled}.</p>
     */
    @Test
    @DisplayName("Type discovery resolves the module's compiled classes")
    void typeDiscoveryIsWiredCorrectly() {
        assertThat(ALL_TYPES)
                .as("no classes discovered under %s — every naming rule would pass vacuously",
                        CLASSES_DIR.toAbsolutePath())
                .hasSizeGreaterThan(400);

        assertThat(ALL_TYPES.stream().filter(f -> packageOf(f).startsWith(KERNEL_PKG)).count())
                .as("the kernel package specifically must be discovered").isGreaterThan(20);

        assertThat(ALL_TYPES.stream().filter(f -> packageOf(f).equals(LAYOUT_PKG)).count())
                .as("kernel.layout must be discovered — rule 6 depends on it").isGreaterThan(10);

        // Sentinels: known types that must be visible, so a future package move cannot quietly empty
        // the rule set.
        assertThat(ALL_TYPES)
                .as("known types must be visible to the rules")
                .contains(KERNEL_PKG + ".RegionLayout",
                        LAYOUT_PKG + ".HeaderLayout64",
                        LAYOUT_PKG + ".StrengthLayout",
                        LAYOUT_PKG + ".AdjacencyListFields");
    }

    /**
     * Documents that the rules are currently violated. When a rename task lands, its rule flips from
     * {@code @Disabled} to enforcing, and the corresponding count here drops to zero.
     *
     * <p>Keeping this enabled means the "currently failing" claim in the javadoc is verified rather
     * than asserted, so the disabled rules cannot rot into passing unnoticed.</p>
     */
    @Test
    @DisplayName("Disabled rules are genuinely violated today (baseline)")
    void disabledRulesHaveRealViolationsToday() {
        long sizeOrVersion = ALL_TYPES.stream()
                .filter(KernelNamingRulesTest::isAuthorNamed)
                .filter(f -> packageOf(f).startsWith(KERNEL_PKG))
                .filter(f -> !f.contains(COMPAT_SEGMENT))
                .filter(f -> !SIZE_SUFFIX_ALLOWLIST.contains(simpleName(f)))
                .filter(f -> SIZE_OR_VERSION_SUFFIX.matcher(simpleName(f)).matches())
                .count();

        long panamaShadow = ALL_TYPES.stream()
                .filter(f -> packageOf(f).startsWith(KERNEL_PKG))
                .filter(f -> PANAMA_TYPES.contains(simpleName(f)))
                .count();

        long shapeTokens = ALL_TYPES.stream()
                .filter(KernelNamingRulesTest::isAuthorNamed)
                .filter(f -> !packageOf(f).startsWith(SHAPE_PKG))
                .filter(f -> !SHAPE_TOKEN_ALLOWLIST.contains(simpleName(f)))
                .filter(f -> SHAPE_TOKEN_SUFFIX.matcher(simpleName(f)).matches())
                .count();

        assertThat(sizeOrVersion).as("expected HeaderLayout64 and HeaderLayout64V2").isGreaterThanOrEqualTo(2);
        assertThat(panamaShadow).as("expected no kernel types shadowing Panama").isEqualTo(0);
        assertThat(shapeTokens).as("expected remaining stores with shape tokens")
                .isGreaterThanOrEqualTo(7);
    }
}
