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
package com.spectrayan.spector.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link SpectorBundleCodec} preserves bytes across a pack/unpack cycle.
 *
 * <p><b>Scope — read this before extending.</b> This class tests the <i>archive</i> layer and nothing
 * else. It builds its own synthetic directory tree, packs it, unpacks it, and compares bytes. It does
 * not start a memory engine, does not export a namespace, does not import one, and therefore says
 * nothing whatsoever about migration parity or data preservation.</p>
 *
 * <p>This class replaces {@code E2EMigrationParityTest}, which was annotated <i>"Export from live
 * docker snapshot, package SMB, unpack into standalone instance, verify 100% parity"</i> and printed
 * <i>"100.00% ZERO-DATA-LOSS MIGRATION VERIFIED"</i> along with hardcoded claims about 641 nodes and a
 * specific WAL file size. It did none of that: it copied a hand-built directory, SHA-256'd it through a
 * zip round trip, and — because it returned early when its source directory was absent — passed
 * unconditionally in CI. Round-trip parity of memory ids, vectors and graph edges is owned by the golden
 * test in {@code memory-portability} (see {@link SpectorBatchUnimplemented#OWNING_SPEC}), which cannot be
 * written until export and import actually move data.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/981">spectrayan/spector#981</a>
 */
@DisplayName("SpectorBundleCodec archive byte-fidelity (pack/unpack only — not migration parity)")
class SpectorBundleArchiveFidelityTest {

    private final SpectorBundleCodec codec = new SpectorBundleCodec();

    @Test
    @DisplayName("Packing then unpacking a directory tree preserves every file's bytes and relative path")
    void packUnpackPreservesBytesAndPaths(@TempDir Path tempDir) throws Exception {
        Path stagingDir = tempDir.resolve("staging");

        // A synthetic tree exercising nesting and both text and binary payloads. These are archive
        // fixtures, deliberately meaningless as memory data — this test asserts nothing about content.
        writeFile(stagingDir.resolve("manifest.json"), "{\"schemaVersion\":\"2.0.0\"}");
        writeFile(stagingDir.resolve("nodes").resolve("chunk-00001.jsonl"), "{\"id\":\"a\"}\n{\"id\":\"b\"}\n");
        writeFile(stagingDir.resolve("nodes").resolve("chunk-00002.jsonl"), "{\"id\":\"c\"}\n");
        writeFile(stagingDir.resolve("graph").resolve("edges.jsonl"), "{\"source\":\"a\",\"target\":\"b\"}\n");
        Files.createDirectories(stagingDir.resolve("vectors"));
        Files.write(stagingDir.resolve("vectors").resolve("chunk-00001.bin"), randomishBytes(4096));

        Path bundle = tempDir.resolve("bundle.smb");
        codec.packageBundle(stagingDir, bundle);

        assertThat(bundle).as("bundle written").exists();
        assertThat(Files.size(bundle)).as("bundle non-empty").isGreaterThan(0L);

        Path unpacked = tempDir.resolve("unpacked");
        codec.unpackBundle(bundle, unpacked);

        List<Path> sourceFiles = listFilesRecursively(stagingDir);
        assertThat(sourceFiles).as("fixture produced files to compare").isNotEmpty();

        for (Path source : sourceFiles) {
            Path relative = stagingDir.relativize(source);
            Path target = unpacked.resolve(relative);

            assertThat(target).as("unpacked file exists: %s", relative).exists();
            assertThat(Files.size(target)).as("size preserved: %s", relative).isEqualTo(Files.size(source));
            assertThat(sha256(target)).as("bytes preserved: %s", relative).isEqualTo(sha256(source));
        }

        assertThat(listFilesRecursively(unpacked))
                .as("unpack introduces no extra files and drops none")
                .hasSameSizeAs(sourceFiles);
    }

    @Test
    @DisplayName("Empty directories are NOT preserved — the codec archives regular files only")
    void emptyDirectoriesAreNotPreserved(@TempDir Path tempDir) throws Exception {
        // Documenting a real limitation rather than asserting the behaviour we would prefer.
        // packageBundle walks regular files, so a member directory containing no files disappears
        // entirely. Any consumer that treats an empty member as "present but empty" will instead see it
        // as missing. memory-portability must either always write at least one file per member or stop
        // validating members by directory presence.
        Path stagingDir = tempDir.resolve("staging");
        Files.createDirectories(stagingDir.resolve("emptyMember"));
        writeFile(stagingDir.resolve("manifest.json"), "{}");

        Path bundle = tempDir.resolve("empty-member.smb");
        codec.packageBundle(stagingDir, bundle);

        Path unpacked = tempDir.resolve("unpacked");
        codec.unpackBundle(bundle, unpacked);

        assertThat(unpacked.resolve("manifest.json")).exists();
        assertThat(unpacked.resolve("emptyMember"))
                .as("empty member directory is lost through the round trip")
                .doesNotExist();
    }

    @Test
    @DisplayName("Nested directories survive the round trip rather than being flattened")
    void packUnpackPreservesNesting(@TempDir Path tempDir) throws Exception {
        Path stagingDir = tempDir.resolve("staging");
        writeFile(stagingDir.resolve("a").resolve("b").resolve("c").resolve("deep.txt"), "deep");

        Path bundle = tempDir.resolve("nested.smb");
        codec.packageBundle(stagingDir, bundle);

        Path unpacked = tempDir.resolve("unpacked");
        codec.unpackBundle(bundle, unpacked);

        assertThat(unpacked.resolve("a").resolve("b").resolve("c").resolve("deep.txt")).exists();
        assertThat(Files.readString(unpacked.resolve("a/b/c/deep.txt"))).isEqualTo("deep");
    }

    @Test
    @DisplayName("A bundle containing a traversal entry is refused rather than written outside the target")
    void unpackRefusesZipSlipEntries(@TempDir Path tempDir) throws Exception {
        // The zip-slip guard in SpectorBundleCodec.unpackBundle was previously untested. It is a security
        // control that the real export/import implementation inherits, so it is covered here rather than
        // left to be re-derived. A crafted bundle is the only way to reach it: packageBundle cannot
        // produce a traversal entry, but a bundle is untrusted input and may not have come from us.
        Path maliciousBundle = tempDir.resolve("malicious.smb");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(maliciousBundle))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("../../escaped.txt"));
            zos.write("should never be written".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Path target = tempDir.resolve("unpack-target");

        assertThatThrownBy(() -> codec.unpackBundle(maliciousBundle, target))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Zip slip")
                .hasMessageContaining("../../escaped.txt");

        assertThat(tempDir.resolve("escaped.txt"))
                .as("traversal entry must not land outside the target directory")
                .doesNotExist();
        assertThat(tempDir.getParent().resolve("escaped.txt"))
                .as("traversal entry must not land above the target directory")
                .doesNotExist();
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    /** Deterministic pseudo-random bytes — reproducible, and not all-zero so truncation is detectable. */
    private static byte[] randomishBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) ((i * 31 + 7) % 251);
        }
        return bytes;
    }

    private static List<Path> listFilesRecursively(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return new ArrayList<>(stream.filter(Files::isRegularFile).toList());
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(file));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
