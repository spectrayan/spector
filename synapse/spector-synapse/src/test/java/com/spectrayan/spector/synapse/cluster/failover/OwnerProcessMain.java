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
package com.spectrayan.spector.synapse.cluster.failover;

import com.spectrayan.spector.cluster.store.JdbcControlStore;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import org.h2.jdbcx.JdbcDataSource;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Real standalone owner process for process-level JVM kill failover testing (P0-1).
 * Runs in a separate forked JVM, connects to a file-backed H2 JdbcControlStore,
 * writes durable engrams to disk WAL/mmap, signals readiness, and waits to be kill -9'd.
 */
public class OwnerProcessMain {

    public static final class SimpleTestEmbedder implements EmbeddingProvider {
        private final int dims;

        public SimpleTestEmbedder(int dims) {
            this.dims = dims;
        }

        @Override
        public int dimensions() {
            return dims;
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vec = new float[dims];
            int hash = Math.abs(text.hashCode());
            for (int i = 0; i < dims; i++) {
                vec[i] = (float) ((hash >> (i % 16)) & 0x0F) / 16.0f;
            }
            return new EmbeddingResult(vec, 1, "simple-embedder");
        }

        @Override
        public String modelName() {
            return "simple-embedder";
        }

        @Override
        public void close() {}
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: OwnerProcessMain <h2DbUrl> <persistenceDir> <namespaceId> <writeCount>");
            System.exit(1);
        }

        String h2DbUrl = args[0];
        Path persistenceDir = Path.of(args[1]);
        String namespaceId = args[2];
        int writeCount = Integer.parseInt(args[3]);

        // 1. Connect to JdbcControlStore via file-backed H2
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(h2DbUrl);
        JdbcControlStore store = new JdbcControlStore(ds, Clock.systemUTC());

        // 2. Fetch or initialize namespace epoch
        long initialEpoch = store.getNamespaceEpoch(namespaceId);
        if (initialEpoch == 0) {
            initialEpoch = store.advanceNamespaceEpoch(namespaceId, 0);
        }

        // 3. Open DefaultSpectorMemory in disk persistence mode
        var memProps = new MemoryProperties()
                .setCapacity(writeCount * 2)
                .setDimensions(64)
                .setWorkingCapacity(10)
                .setEpisodicPartitionCapacity(writeCount * 2)
                .setSemanticCapacity(writeCount * 2)
                .setProceduralCapacity(writeCount * 2);
        memProps.getRemember().setSurpriseWarmup(1);

        SpectorMemory memory = DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(new SimpleTestEmbedder(64))
                .namespaceId(namespaceId)
                .persistence(persistenceDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .build();

        // 4. Ingest durable memories
        for (int i = 0; i < writeCount; i++) {
            String id = "durable-mem-" + String.format("%03d", i);
            String text = "Durable observation content payload #" + i + " written by owner process";
            memory.remember(id, text, MemoryType.SEMANTIC, MemorySource.OBSERVED, "process-kill", "durable");
        }

        // 5. Signal readiness to parent process on stdout
        System.out.println("OWNER_READY:" + initialEpoch + ":" + writeCount);
        System.out.flush();

        // 6. Block indefinitely without graceful shutdown until killed with SIGKILL (destroyForcibly)
        while (true) {
            Thread.sleep(1000);
        }
    }
}
