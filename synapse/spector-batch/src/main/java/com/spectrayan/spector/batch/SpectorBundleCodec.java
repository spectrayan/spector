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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Handles archive packaging and unpacking for Spector Memory Bundles (.smb).
 *
 * <p>A Spector Memory Bundle is a standard ZIP container with per-entry DEFLATE compression
 * containing the following member layout (ADR-0045, memory-portability R3, R6):
 * <ul>
 *   <li>{@code manifest.json}: schemaVersion (3.0.0), embedding descriptor (model, dimensions, quantizer),
 *       namespaceId, counts (records, edges, hyperedges, facts), per-member SHA-256 integrity checksums,
 *       and sourceBuildVersion (R3.1, R3.2)</li>
 *   <li>{@code nodes/chunk-NNNNN.jsonl}: streamable JSONL memory records (node IDs, text, tags, key-values,
 *       salience, decay, timestamps)</li>
 *   <li>{@code vectors/chunk-NNNNN.bin}: raw float arrays, ordinal-aligned to nodes and described by manifest</li>
 *   <li>{@code graph/edges.jsonl}: cognitive graph connections and Hebbian weights with endpoint IDs</li>
 *   <li>{@code graph/hyperedges.jsonl}: typed hyperedges and roles (HyperEntityGraphMemory)</li>
 *   <li>{@code graph/facts.jsonl}: temporal facts and validity intervals</li>
 *   <li>{@code subsystems/state.json}: biological subsystem parameters (optional, omitted if unavailable)</li>
 *   <li>{@code security/keys.json}: omitted until Phase 6 DEK exists (R1.5, ADR-0034 D6)</li>
 * </ul>
 * </p>
 */
public class SpectorBundleCodec {

    private static final Logger log = LoggerFactory.getLogger(SpectorBundleCodec.class);

    /**
     * Packages a directory into a Spector Memory Bundle (.smb archive).
     *
     * @param sourceDir directory containing staging export files
     * @param outputFile target .smb output path
     * @throws IOException if packaging fails
     */
    public void packageBundle(Path sourceDir, Path outputFile) throws IOException {
        log.info("[SpectorBundleCodec] Packaging bundle from {} to {}", sourceDir, outputFile);
        
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile.toFile())))) {
            Files.walk(sourceDir).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(entryName);
                try {
                    zos.putNextEntry(entry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to package entry: " + entryName, e);
                }
            });
        }
        log.info("[SpectorBundleCodec] Successfully packaged bundle: {}", outputFile);
    }

    /**
     * Extracts a Spector Memory Bundle (.smb archive) into a destination directory.
     *
     * <p>Enforces pre-write validation: the archive structure is validated (detecting
     * corruption or truncation) and all entry names are scanned for zip-slip traversal
     * attacks <b>before</b> creating the target directory or writing any files to disk
     * (R6.5, V6).</p>
     *
     * @param bundleFile input .smb file
     * @param targetDir directory to unpack into
     * @throws IOException if extraction fails or security violation occurs
     */
    public void unpackBundle(Path bundleFile, Path targetDir) throws IOException {
        log.info("[SpectorBundleCodec] Unpacking bundle {} into {}", bundleFile, targetDir);
        Objects.requireNonNull(bundleFile, "bundleFile must not be null");
        Objects.requireNonNull(targetDir, "targetDir must not be null");

        if (!Files.exists(bundleFile)) {
            throw new SpectorValidationException(
                    ErrorCode.CONFIG_FILE_NOT_FOUND,
                    "Bundle file not found: " + bundleFile
            );
        }

        // 1. Verify archive structure with ZipFile (validates central directory).
        // If truncated, corrupted, or not a zip, this throws before targetDir is created.
        try (ZipFile zipFile = new ZipFile(bundleFile.toFile())) {
            // 2. Pre-scan pass: check for Zip Slip traversal attacks on ALL entries
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            Path normalizedTarget = targetDir.toAbsolutePath().normalize();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path resolvePath = normalizedTarget.resolve(entry.getName()).normalize();
                if (!resolvePath.startsWith(normalizedTarget)) {
                    throw new IOException("Zip slip security violation for entry: " + entry.getName());
                }
            }

            // 3. Central directory and entry paths are verified. Now create destination directory.
            Files.createDirectories(targetDir);

            // 4. Extract entries
            entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path resolvePath = normalizedTarget.resolve(entry.getName()).normalize();
                if (!resolvePath.startsWith(normalizedTarget)) {
                    throw new IOException("Zip slip security violation for entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolvePath);
                } else {
                    if (resolvePath.getParent() != null) {
                        Files.createDirectories(resolvePath.getParent());
                    }
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, resolvePath);
                    }
                }
            }
        } catch (ZipException e) {
            throw new SpectorValidationException(
                    ErrorCode.FILE_FORMAT_INVALID,
                    "Corrupt or truncated SMB bundle: " + e.getMessage()
            );
        }
        log.info("[SpectorBundleCodec] Successfully unpacked bundle into {}", targetDir);
    }

    /**
     * Reads and parses {@code manifest.json} directly from an .smb archive without
     * unpacking member files or creating staging directories (R3.3, V6).
     *
     * @param bundleFile path to .smb bundle
     * @return parsed SpectorBundleManifest
     * @throws IOException if an I/O error occurs
     */
    public SpectorBundleManifest readManifest(Path bundleFile) throws IOException {
        Objects.requireNonNull(bundleFile, "bundleFile must not be null");
        if (!Files.exists(bundleFile)) {
            throw new SpectorValidationException(
                    ErrorCode.CONFIG_FILE_NOT_FOUND,
                    "Bundle file not found: " + bundleFile
            );
        }

        try (ZipFile zipFile = new ZipFile(bundleFile.toFile())) {
            ZipEntry manifestEntry = zipFile.getEntry("manifest.json");
            if (manifestEntry == null) {
                throw new SpectorValidationException(
                        ErrorCode.FILE_FORMAT_INVALID,
                        "Invalid SMB bundle: missing manifest.json"
                );
            }
            try (InputStream is = zipFile.getInputStream(manifestEntry)) {
                return SpectorBundleManifest.fromJson(is);
            }
        } catch (ZipException e) {
            throw new SpectorValidationException(
                    ErrorCode.FILE_FORMAT_INVALID,
                    "Corrupt or truncated SMB bundle: " + e.getMessage()
            );
        }
    }

    /**
     * Verifies member checksums in an .smb archive against the recorded manifest (R3.2).
     *
     * @param bundleFile path to .smb bundle
     * @param manifest manifest containing per-member checksums
     * @throws IOException if I/O fails or checksum validation fails
     */
    public void verifyChecksums(Path bundleFile, SpectorBundleManifest manifest) throws IOException {
        Objects.requireNonNull(bundleFile, "bundleFile must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");

        if (manifest.checksums() == null || manifest.checksums().isEmpty()) {
            return;
        }

        try (ZipFile zipFile = new ZipFile(bundleFile.toFile())) {
            for (Map.Entry<String, String> entry : manifest.checksums().entrySet()) {
                String memberName = entry.getKey();
                String expectedSha256 = entry.getValue();

                ZipEntry zipEntry = zipFile.getEntry(memberName);
                if (zipEntry == null) {
                    throw new SpectorValidationException(
                            ErrorCode.FILE_FORMAT_INVALID,
                            "Missing bundle member recorded in manifest checksums: " + memberName
                    );
                }

                String actualSha256;
                try (InputStream is = zipFile.getInputStream(zipEntry)) {
                    actualSha256 = computeSha256(is);
                }

                String normalizedExpected = expectedSha256.startsWith("sha256:")
                        ? expectedSha256.substring(7) : expectedSha256;
                if (!actualSha256.equalsIgnoreCase(normalizedExpected)) {
                    throw new SpectorValidationException(
                            ErrorCode.RECORD_CRC_CORRUPTED,
                            memberName + " (expected: " + normalizedExpected + ", got: " + actualSha256 + ")"
                    );
                }
            }
        } catch (ZipException e) {
            throw new SpectorValidationException(
                    ErrorCode.FILE_FORMAT_INVALID,
                    "Corrupt or truncated SMB bundle: " + e.getMessage()
            );
        }
    }

    /**
     * Computes the SHA-256 hash of an InputStream.
     */
    public static String computeSha256(InputStream is) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * Computes the SHA-256 hash of a file on disk.
     */
    public static String computeSha256(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            return computeSha256(is);
        }
    }

    /**
     * Computes relative path to SHA-256 checksums for all regular files in a staging directory,
     * excluding {@code manifest.json}.
     */
    public static Map<String, String> computeMemberChecksums(Path stagingDir) throws IOException {
        Map<String, String> checksums = new TreeMap<>();
        try (var stream = Files.walk(stagingDir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String relative = stagingDir.relativize(file).toString().replace('\\', '/');
                if (!"manifest.json".equals(relative)) {
                    checksums.put(relative, computeSha256(file));
                }
            }
        }
        return checksums;
    }
}
