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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpectorBundleCodec Unit Tests")
class SpectorBundleCodecTest {

    private final SpectorBundleCodec codec = new SpectorBundleCodec();

    @Test
    @DisplayName("Should package staging directory into .smb archive and unpack with full parity")
    void testPackageAndUnpackBundle(@TempDir Path tempDir) throws IOException {
        Path stagingDir = tempDir.resolve("staging");
        Files.createDirectories(stagingDir.resolve("nodes"));
        Files.createDirectories(stagingDir.resolve("vectors"));
        Files.createDirectories(stagingDir.resolve("graph"));

        Files.writeString(stagingDir.resolve("manifest.json"), "{\"schemaVersion\":\"2.0.0\"}");
        Files.writeString(stagingDir.resolve("nodes").resolve("chunk-00001.jsonl"), "{\"id\":\"n1\",\"text\":\"hello\"}");
        Files.write(stagingDir.resolve("vectors").resolve("vec.bin"), new byte[]{1, 2, 3, 4});

        Path smbFile = tempDir.resolve("bundle.smb");
        codec.packageBundle(stagingDir, smbFile);

        assertThat(Files.exists(smbFile)).isTrue();
        assertThat(Files.size(smbFile)).isGreaterThan(0);

        Path unpackDir = tempDir.resolve("unpacked");
        codec.unpackBundle(smbFile, unpackDir);

        assertThat(unpackDir.resolve("manifest.json")).exists();
        assertThat(Files.readString(unpackDir.resolve("manifest.json"))).contains("2.0.0");
        assertThat(unpackDir.resolve("nodes").resolve("chunk-00001.jsonl")).exists();
        assertThat(unpackDir.resolve("vectors").resolve("vec.bin")).exists();
    }
}
