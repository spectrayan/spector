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
package com.spectrayan.spector.memory.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;

class StoreDirectoryLockTest {

    @TempDir
    Path dir;

    @Test
    void secondOpenOfTheSameStoreIsRefusedAndCloseReleasesIt() {
        SpectorMemory first = open(dir);
        try {
            assertThat(StoreDirectoryLock.isHeld(dir)).isTrue();
            assertThatThrownBy(() -> open(dir))
                    .isInstanceOfSatisfying(SpectorConfigException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.STORE_LOCKED));
        } finally {
            first.close();
        }
        assertThat(StoreDirectoryLock.isHeld(dir)).isFalse();
        try (SpectorMemory again = open(dir)) {
            assertThat(StoreDirectoryLock.isHeld(dir)).isTrue();
        }
    }

    @Test
    void lockIsPerDirectory(@TempDir Path other) {
        StoreDirectoryLock.acquire(dir);
        try {
            StoreDirectoryLock.acquire(other);
            StoreDirectoryLock.release(other);
            assertThatThrownBy(() -> StoreDirectoryLock.acquire(dir))
                    .isInstanceOfSatisfying(SpectorConfigException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.STORE_LOCKED));
        } finally {
            StoreDirectoryLock.release(dir);
        }
        StoreDirectoryLock.release(dir); // idempotent
    }

    @Test
    void anotherProcessHoldingTheLockIsDetected() throws Exception {
        Files.createDirectories(dir);
        Path lockFile = dir.resolve(StoreDirectoryLock.LOCK_FILE);
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // A child JVM takes the OS lock and waits for stdin to close.
        Path src = dir.getParent().resolve("Holder" + System.nanoTime() + ".java");
        Files.writeString(src, "import java.nio.channels.*;import java.nio.file.*;"
                + "public class Holder{public static void main(String[] a)throws Exception{"
                + "try(FileChannel c=FileChannel.open(Path.of(a[0]),StandardOpenOption.CREATE,StandardOpenOption.WRITE);"
                + "FileLock l=c.lock()){System.out.println(\"locked\");System.out.flush();System.in.read();}}}");
        Process holder = new ProcessBuilder(javaBin, src.toString(), lockFile.toString()).redirectErrorStream(true).start();
        try {
            var out = new BufferedReader(new InputStreamReader(holder.getInputStream()));
            assertThat(out.readLine()).isEqualTo("locked");
            assertThatThrownBy(() -> StoreDirectoryLock.acquire(dir))
                    .isInstanceOfSatisfying(SpectorConfigException.class,
                            e -> assertThat(e.getMessage()).contains("another process"));
        } finally {
            holder.getOutputStream().close();
            holder.waitFor(20, TimeUnit.SECONDS);
            holder.destroyForcibly();
            Files.deleteIfExists(src);
        }
        StoreDirectoryLock.acquire(dir);
        StoreDirectoryLock.release(dir);
    }

    private static SpectorMemory open(Path dir) {
        FakeEmbeddingProvider embed = new FakeEmbeddingProvider();
        MemoryProperties props = new MemoryProperties()
                .setDimensions(embed.dimensions())
                .setWorkingCapacity(10)
                .setEpisodicPartitionCapacity(50)
                .setSemanticCapacity(50)
                .setProceduralCapacity(50);
        return DefaultSpectorMemory.builder(props)
                .embeddingProvider(embed)
                .namespaceId("store-lock")
                .persistence(dir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .build();
    }
}
