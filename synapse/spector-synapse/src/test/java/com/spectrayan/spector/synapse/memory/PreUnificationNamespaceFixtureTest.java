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

import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 0.1: Pre-unification namespace fixture and backward-compatibility test.
 * <p>
 * Ensures a pre-change namespace tree (namespace.json + runtime.bundle + sealed and active
 * partition.bundle + WAL segment) opens unchanged when untenanted, matching record counts
 * and partition structure (R11.4).
 */
@DisplayName("Pre-unification namespace fixture compatibility")
class PreUnificationNamespaceFixtureTest {

    private static final String RESOURCE_PATH = "namespace-layout/pre-unification";
    private static final int DIMENSIONS = 4;
    private static final int WORKING_CAP = 4;
    private static final int SEMANTIC_CAP = 2;
    private static final int EPISODIC_CAP = 4;
    private static final int PROCEDURAL_CAP = 4;

    static MemoryProperties testProperties() {
        var memProps = new MemoryProperties()
                .setDimensions(DIMENSIONS)
                .setWorkingCapacity(WORKING_CAP)
                .setEpisodicPartitionCapacity(EPISODIC_CAP)
                .setSemanticCapacity(SEMANTIC_CAP)
                .setProceduralCapacity(PROCEDURAL_CAP);
        memProps.getRemember().setSurpriseWarmup(1);
        return memProps;
    }

    static SpectorMemory openMemory(Path dir) {
        return DefaultSpectorMemory.builder(testProperties())
                .embeddingProvider(new TestEmbedder(DIMENSIONS))
                .persistenceMode(MemoryPersistenceMode.DISK)
                .persistence(dir)
                .build();
    }

    @Test
    @DisplayName("Pre-unification namespace fixture opens unchanged with exact record counts")
    void preUnificationNamespaceOpensWithExactRecordCounts(@TempDir Path tempDir) throws Exception {
        Path fixtureTarget = tempDir.resolve("fixture-ns");
        copyFixtureFromClasspath(fixtureTarget);

        // Verify layout elements exist in fixture before opening
        assertThat(fixtureTarget.resolve(StoragePaths.FILE_NAMESPACE)).exists();
        assertThat(fixtureTarget.resolve(StoragePaths.DIR_RUNTIME).resolve(StoragePaths.FILE_RUNTIME_BUNDLE)).exists();
        assertThat(fixtureTarget.resolve(StoragePaths.DIR_PARTITIONS)).exists();
        assertThat(fixtureTarget.resolve(StoragePaths.DIR_WAL)).exists();

        try (SpectorMemory memory = openMemory(fixtureTarget)) {
            // Verify semantic memories
            CognitiveRecord sem0 = memory.inspect("sem-0");
            CognitiveRecord sem1 = memory.inspect("sem-1");
            CognitiveRecord sem2 = memory.inspect("sem-2");
            CognitiveRecord epi0 = memory.inspect("epi-0");

            assertThat(sem0).as("sem-0 record").isNotNull();
            assertThat(sem1).as("sem-1 record").isNotNull();
            assertThat(sem2).as("sem-2 record").isNotNull();
            assertThat(epi0).as("epi-0 record").isNotNull();

            assertThat(sem0.text()).isEqualTo("Pre-unification semantic memory zero");
            assertThat(sem1.text()).isEqualTo("Pre-unification semantic memory one");
            assertThat(sem2.text()).isEqualTo("Pre-unification semantic memory two");
            assertThat(epi0.text()).isEqualTo("Pre-unification episodic event zero");

            assertThat(sem0.memoryType()).isEqualTo(MemoryType.SEMANTIC);
            assertThat(sem1.memoryType()).isEqualTo(MemoryType.SEMANTIC);
            assertThat(sem2.memoryType()).isEqualTo(MemoryType.SEMANTIC);
            assertThat(epi0.memoryType()).isEqualTo(MemoryType.EPISODIC);

            assertThat(memory.memoryCount(MemoryType.SEMANTIC)).isEqualTo(3);
            assertThat(memory.memoryCount(MemoryType.EPISODIC)).isEqualTo(1);

            // Verify physical partition directories in fixture (sealed 000 + active 001)
            long partitionDirCount = countPartitionDirs(fixtureTarget);
            assertThat(partitionDirCount).as("partition directory count").isEqualTo(2);
        }

        // K1/I7 backstop: verify opening a different/empty directory does NOT find these records
        Path emptyTarget = tempDir.resolve("empty-ns");
        Files.createDirectories(emptyTarget);
        try (SpectorMemory emptyMemory = openMemory(emptyTarget)) {
            assertThat(emptyMemory.inspect("sem-0")).isNull();
            assertThat(emptyMemory.inspect("sem-1")).isNull();
            assertThat(emptyMemory.inspect("sem-2")).isNull();
            assertThat(emptyMemory.inspect("epi-0")).isNull();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "spector.fixture.regenerate", matches = "true",
            disabledReason = "Fixture generator — opt in with -Dspector.fixture.regenerate=true")
    @DisplayName("Regenerate pre-unification namespace fixture")
    void regenerateFixture() throws Exception {
        Path srcResources = Paths.get("src", "test", "resources", RESOURCE_PATH);
        if (Files.exists(srcResources)) {
            deleteRecursively(srcResources);
        }
        Files.createDirectories(srcResources);

        Path work = Files.createTempDirectory("pre-unif-fixture-gen");
        try {
            try (SpectorMemory mem = openMemory(work)) {
                // Populate 2 semantic memories to fill partition 0 (capacity 2)
                mem.remember("sem-0", "Pre-unification semantic memory zero",
                        MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra");
                mem.remember("sem-1", "Pre-unification semantic memory one",
                        MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra");

                // 3rd semantic memory overflows partition 0 and rolls to partition 1 (sealed + active)
                mem.remember("sem-2", "Pre-unification semantic memory two",
                        MemoryType.SEMANTIC, MemorySource.USER_STATED, "infra");

                // 1 episodic memory
                mem.remember("epi-0", "Pre-unification episodic event zero",
                        MemoryType.EPISODIC, MemorySource.OBSERVED, "events");
            }

            // Write namespace.json marker
            String namespaceJson = """
                    {
                      "id": "pre-unif-ns",
                      "display_name": "Pre-Unification Fixture",
                      "max_memories": 1000,
                      "max_partitions": 10,
                      "max_storage_bytes": 104857600,
                      "read_only": false,
                      "created_at": "2026-09-11T00:00:00Z"
                    }
                    """;
            Files.writeString(work.resolve(StoragePaths.FILE_NAMESPACE), namespaceJson);

            // Copy populated tree to src/test/resources, gzipping .bundle files for compact repo size
            Files.walkFileTree(work, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path rel = work.relativize(dir);
                    Path dest = srcResources.resolve(rel);
                    Files.createDirectories(dest);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path rel = work.relativize(file);
                    if (file.getFileName().toString().endsWith(".bundle")) {
                        Path gzDest = srcResources.resolve(rel.toString() + ".gz");
                        try (InputStream in = Files.newInputStream(file);
                             OutputStream out = Files.newOutputStream(gzDest);
                             GZIPOutputStream gzOut = new GZIPOutputStream(out)) {
                            in.transferTo(gzOut);
                        }
                    } else {
                        Path dest = srcResources.resolve(rel);
                        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            System.out.println("Generated pre-unification fixture at: " + srcResources.toAbsolutePath());
        } finally {
            deleteRecursively(work);
        }
    }

    private static void copyFixtureFromClasspath(Path target) throws Exception {
        var resourceUrl = PreUnificationNamespaceFixtureTest.class.getClassLoader().getResource(RESOURCE_PATH);
        assertThat(resourceUrl).as("Fixture resource %s must be on test classpath", RESOURCE_PATH).isNotNull();
        URI uri = resourceUrl.toURI();
        Path sourcePath = Paths.get(uri);

        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = sourcePath.relativize(dir);
                Path dest = target.resolve(rel);
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = sourcePath.relativize(file);
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".bundle.gz")) {
                    String uncompressedName = fileName.substring(0, fileName.length() - 3);
                    Path dest = target.resolve(rel).resolveSibling(uncompressedName);
                    try (InputStream in = Files.newInputStream(file);
                         GZIPInputStream gzIn = new GZIPInputStream(in);
                         OutputStream out = Files.newOutputStream(dest)) {
                        gzIn.transferTo(out);
                    }
                } else {
                    Path dest = target.resolve(rel);
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static long countPartitionDirs(Path base) throws IOException {
        Path partDir = StoragePaths.partitionsDir(base);
        if (!Files.exists(partDir)) return 0;
        try (var stream = Files.newDirectoryStream(partDir)) {
            long count = 0;
            for (Path p : stream) {
                if (Files.isDirectory(p) && StoragePaths.isPartitionDir(p.getFileName().toString())) {
                    count++;
                }
            }
            return count;
        }
    }

    static final class TestEmbedder implements EmbeddingProvider {
        private final int dims;

        TestEmbedder(int dims) {
            this.dims = dims;
        }

        @Override
        public int dimensions() {
            return dims;
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = new float[dims];
            vec[0] = 1.0f;
            return new EmbeddingResult(vec, 1, "test-embedder");
        }

        @Override
        public String modelName() {
            return "test-embedder";
        }

        @Override
        public void close() {
        }
    }
}
