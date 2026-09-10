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
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.kernel.score.EdgeImportance;
import com.spectrayan.spector.kernel.store.HebbianEdge;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.kernel.store.HebbianNeighborProvider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Migration step that converts legacy fixed-width Hebbian graphs ('HGPH')
 * to CSR format ('HCSR').
 */
public final class HgphToCsrStep extends RewriteFileStep {

    public static final int FROM_MAGIC = Integer.reverseBytes(HebbianGraphMemory.LEGACY_HGPH_MAGIC);
    public static final FormatId FROM_FORMAT = new FormatId(FROM_MAGIC, 1);
    public static final FormatId TO_FORMAT = FormatId.smkm(1);

    @Override
    public FormatId from() {
        return FROM_FORMAT;
    }

    @Override
    public FormatId to() {
        return TO_FORMAT;
    }

    @Override
    protected void rewrite(Path source, Path target, MigrationContext ctx) throws IOException {
        int maxDegree = 20;
        try (FileChannel ch = FileChannel.open(source, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(16);
            ch.read(header);
            header.flip();
            int magic = header.getInt();
            int version = header.getInt();
            int fileCapacity = header.getInt();
            int fileDegree = header.getInt();
            if (fileDegree > 0) {
                maxDegree = fileDegree;
            }

            int edgeBytes = (version < 2) ? 8 : 12;
            int nodeBytes = 4 + maxDegree * edgeBytes;
            long dataBytes = (long) nodeBytes * fileCapacity;

            ByteBuffer data = ByteBuffer.allocate((int) Math.min(dataBytes, ch.size() - 16));
            ch.read(data);
            data.flip();

            List<List<HebbianEdge>> adj = new ArrayList<>(fileCapacity);
            int totalEdges = 0;
            for (int i = 0; i < fileCapacity; i++) {
                int nodeOffset = i * nodeBytes;
                if (nodeOffset + 4 > data.limit()) break;
                int deg = data.getInt(nodeOffset);
                List<HebbianEdge> edges = new ArrayList<>();
                if (deg > 0 && deg <= maxDegree) {
                    for (int e = 0; e < deg; e++) {
                        int eOff = nodeOffset + 4 + e * edgeBytes;
                        if (eOff + edgeBytes > data.limit()) break;
                        int neighbor = data.getInt(eOff);
                        float weight = data.getFloat(eOff + 4);
                        int bridge = (edgeBytes >= 12) ? (data.get(eOff + 8) & 0xFF) : 0;
                        edges.add(new HebbianEdge(neighbor, weight, bridge));
                        totalEdges++;
                    }
                }
                adj.add(edges);
            }

            final int finalTotalEdges = totalEdges;
            final int finalCap = fileCapacity;
            HebbianNeighborProvider provider = new HebbianNeighborProvider() {
                @Override public int capacity() { return finalCap; }
                @Override public int totalEdges() { return finalTotalEdges; }
                @Override public List<HebbianEdge> neighbors(int node) {
                    return node < adj.size() ? adj.get(node) : List.of();
                }
            };

            HebbianGraphMemory csr = HebbianGraphMemory.fromNeighbors(provider, maxDegree, EdgeImportance.DEFAULT);
            try {
                csr.save(target);
            } finally {
                csr.close();
            }
        }
    }
}
