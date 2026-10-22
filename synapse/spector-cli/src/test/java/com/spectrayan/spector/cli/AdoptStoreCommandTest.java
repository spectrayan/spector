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
package com.spectrayan.spector.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.memory.persist.StoreDirectoryLock;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;

class AdoptStoreCommandTest {

    @TempDir
    Path temp;

    @Test
    void dryRunCountsFromACopyAndLeavesTheStoreUntouched() throws Exception {
        Path store = temp.resolve("memory");
        Files.createDirectories(store);
        Files.writeString(store.resolve("marker.txt"), "keep");
        try (MemoryWal wal = new MemoryWal(store.resolve("wal"))) {
            wal.append(WalEvent.EventType.REMEMBER, "live-1", new byte[0]);
            wal.append(WalEvent.EventType.REMEMBER, "gone", new byte[0]);
            wal.append(WalEvent.EventType.FORGET, "gone", new byte[0]);
        }

        AdoptStoreCommand.Counts counts = AdoptStoreCommand.countCopy(store);
        assertThat(counts.live()).isEqualTo(1);
        assertThat(counts.forgotten()).isEqualTo(1);
        assertThat(Files.readString(store.resolve("marker.txt"))).isEqualTo("keep");
        assertThat(Files.exists(store.resolve("namespaces"))).isFalse();
    }

    @Test
    void realRunMovesTheFlatStoreAndKeepsABackup() throws Exception {
        Path store = temp.resolve("memory");
        Files.createDirectories(store);
        Files.writeString(store.resolve("marker.txt"), "keep");

        AdoptStoreCommand.Adoption plan = AdoptStoreCommand.plan(store, "grokbot");
        AdoptStoreCommand.execute(plan);

        assertThat(Files.readString(plan.target().resolve("marker.txt"))).isEqualTo("keep");
        assertThat(Files.exists(store.resolve("marker.txt"))).isFalse();
        assertThat(Files.readString(plan.backup().resolve("marker.txt"))).isEqualTo("keep");
    }

    @Test
    void refusesWhenTheTargetAlreadyHoldsAStore() throws Exception {
        Path store = temp.resolve("memory");
        Path target = store.resolve("namespaces").resolve("grokbot");
        Files.createDirectories(target);
        Files.writeString(target.resolve("marker.txt"), "already");
        assertThatThrownBy(() -> AdoptStoreCommand.plan(store, "grokbot"))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("already holds a store");
    }

    @Test
    void refusesWhenTheStoreLockIsHeld() throws Exception {
        Path store = temp.resolve("memory");
        Files.createDirectories(store);
        StoreDirectoryLock.acquire(store);
        try {
            assertThatThrownBy(() -> AdoptStoreCommand.plan(store, "grokbot"))
                    .isInstanceOf(SpectorConfigException.class)
                    .hasMessageContaining("SPE-310-031");
        } finally {
            StoreDirectoryLock.release(store);
        }
    }
}
