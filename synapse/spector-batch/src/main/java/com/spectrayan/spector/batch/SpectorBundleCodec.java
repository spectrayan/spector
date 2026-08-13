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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Handles archive packaging and unpacking for Spector Memory Bundles (.smb).
 *
 * <p>A Spector Memory Bundle contains:
 * <ul>
 *   <li>manifest.json (versioning, CRC32, entity counts)</li>
 *   <li>nodes/ (memory texts, tags, key-values, salience, decay)</li>
 *   <li>vectors/ (raw float vectors, index state)</li>
 *   <li>graph/ (cognitive hyperedges, Hebbian weights)</li>
 *   <li>subsystems/ (biological subsystem parameters)</li>
 *   <li>security/ (encryption header metadata)</li>
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
     * @param bundleFile input .smb file
     * @param targetDir directory to unpack into
     * @throws IOException if extraction fails
     */
    public void unpackBundle(Path bundleFile, Path targetDir) throws IOException {
        log.info("[SpectorBundleCodec] Unpacking bundle {} into {}", bundleFile, targetDir);
        Files.createDirectories(targetDir);

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(bundleFile.toFile())))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolvePath = targetDir.resolve(entry.getName()).normalize();
                if (!resolvePath.startsWith(targetDir.normalize())) {
                    throw new IOException("Zip slip security violation for entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvePath);
                } else {
                    if (resolvePath.getParent() != null) {
                        Files.createDirectories(resolvePath.getParent());
                    }
                    Files.copy(zis, resolvePath);
                }
                zis.closeEntry();
            }
        }
        log.info("[SpectorBundleCodec] Successfully unpacked bundle into {}", targetDir);
    }
}
