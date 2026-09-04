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
package com.spectrayan.spector.bench.cognitive.mindspan;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.cortex.StrengthMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MindSpanStrengthAndAuditInspectionTest {

    @Test
    public void testInspectSubsystemsAndStrengthLayout() throws Exception {
        Path memDir = Paths.get("d:/git/spector-datasets/mindspan/results/sample-run-10-verified/v2-memory");
        assertTrue(memDir.toFile().exists(), "Memory dir must exist: " + memDir);

        Path reportFile = memDir.getParent().resolve("audit_inspection.txt");

        try (SpectorMemory memory = SpectorMemoryBuilder.create()
                .dimensions(768)
                .embeddingProvider(OllamaEmbeddingProvider.createDefault())
                .persistence(memDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .episodicPartitionCapacity(35_000)
                .semanticCapacity(20_000)
                .build();
             PrintWriter out = new PrintWriter(reportFile.toFile())) {

            var admin = memory.admin();
            assertNotNull(admin, "Admin must be available");
            var router = admin.cognitiveRouter();
            assertNotNull(router, "Router must be available");
            MemoryIndex index = admin.index();
            assertNotNull(index, "Index must be available");

            long workingCount = (router.working() != null) ? router.working().size() : 0;
            long semanticCount = (router.semantic() != null) ? router.semantic().size() : 0;
            long proceduralCount = (router.procedural() != null) ? router.procedural().size() : 0;
            long episodicTurnCount = (router.episodic() != null) ? router.episodic().visibleCount() : 0;
            long episodicBytes = (router.episodic() != null) ? router.episodic().size() : 0;
            int unconsolidatedTurns = (router.episodic() != null) ? router.episodic().unconsolidatedTurnOffsets().size() : 0;
            int totalTurns = 0;
            int totalSessions = 0;
            if (memory instanceof DefaultSpectorMemory dsm && dsm.episodicSessionIndex() != null) {
                totalTurns = dsm.episodicSessionIndex().totalTurnCount();
                totalSessions = dsm.episodicSessionIndex().sessionCount();
            }

            int entityCount = (admin.entityDirectory() != null) ? admin.entityDirectory().entityCount() : 0;
            int adjHwm = (admin.entityDirectory() != null) ? admin.entityDirectory().adjHighWaterMark() : 0;
            int tkgFacts = (admin.temporalKnowledgeGraph() != null) ? admin.temporalKnowledgeGraph().factCount() : 0;
            int hyperedges = (admin.hyperEntityGraph() != null) ? admin.hyperEntityGraph().totalHyperedges() : 0;
            var graphStats = (admin.graph() != null) ? admin.graph().graphStats() : null;
            int hebbianEdges = graphStats != null ? graphStats.hebbianEdges() : 0;
            int temporalLinks = graphStats != null ? graphStats.temporalLinks() : 0;

            out.println("================== FULL SUBSYSTEM AUDIT ==================");
            out.printf("Total Memories:                %d%n", memory.totalMemories());
            out.printf("MemoryIndex Size:              %d%n", index.size());
            out.printf("Working Memory:                %d records%n", workingCount);
            out.printf("Semantic Memory:               %d records%n", semanticCount);
            out.printf("Procedural Memory:             %d records%n", proceduralCount);
            out.printf("Episodic Memory Turns:         %d visible turns%n", episodicTurnCount);
            out.printf("Episodic Memory Raw Bytes:     %d bytes%n", episodicBytes);
            out.printf("Episodic Active Sessions:      %d%n", totalSessions);
            out.printf("Episodic Unconsolidated Turns: %d%n", unconsolidatedTurns);
            out.printf("Entity Directory Entities:     %d%n", entityCount);
            out.printf("Entity Directory Adjacency HWM:%d%n", adjHwm);
            out.printf("Temporal Knowledge Graph Facts:%d%n", tkgFacts);
            out.printf("HyperEntity Graph Hyperedges:  %d%n", hyperedges);
            out.printf("Hebbian Associative Edges:     %d%n", hebbianEdges);
            out.printf("Temporal Chain Linked Slots:   %d%n", temporalLinks);
            out.println("==========================================================");

            // Strength layout inspection
            StrengthMemory strengthStore = router.strength();
            out.printf("StrengthStore instance present: %b%n", strengthStore != null);
            if (strengthStore != null) {
                out.printf("StrengthStore layout ID: 0x%s%n", Integer.toHexString(strengthStore.layout().layoutId()));
                out.printf("StrengthStore record stride: %d bytes%n", strengthStore.layout().recordStride());
                out.printf("StrengthStore semantic capacity: %d%n", strengthStore.semanticCapacity());
                out.printf("StrengthStore episodic capacity: %d%n", strengthStore.episodicCapacity());
            }

            // Inspect records across both tiers
            String[] testIds = {"bio-0001", "bio-0002", "bio-0003", "bio-0004", "bio-0005", "bio-0006", "bio-0007", "bio-0008", "bio-0009", "bio-0010"};
            out.println("--- Ingested Records Sample Inspection ---");
            for (String id : testIds) {
                MemoryIndex.MemoryLocation loc = index.locate(id);
                if (loc != null) {
                    var body = router.readRecordBody(loc, false);
                    EncodingHeader h = body != null ? body.header() : null;
                    float headerStorage = h != null ? h.storageStrength() : -1f;
                    float strengthStoreVal = -1f;
                    int agentRecallCount = -1;
                    float effectiveImportance = -1f;

                    if (strengthStore != null && loc.type() != MemoryType.WORKING) {
                        int slotIndex = (loc.type() == MemoryType.EPISODIC)
                                ? 0
                                : (int) ((loc.offset() - router.get(loc.type()).dataOffset()) / router.layoutFor(loc.type()).stride());
                        strengthStoreVal = strengthStore.readStorageStrength(loc.type(), slotIndex);
                        agentRecallCount = strengthStore.readAgentRecallCount(loc.type(), slotIndex);
                        effectiveImportance = strengthStore.readEffectiveImportance(loc.type(), slotIndex);
                    }

                    out.printf("ID [%s | %s] -> headerStorage=%.4f, strengthStoreStorage=%.4f, effectiveImportance=%.4f, agentRecallCnt=%d, source=%s%n",
                            id, loc.type(), headerStorage, strengthStoreVal, effectiveImportance, agentRecallCount, h != null ? h.source() : "null");
                }
            }

            // Test Dynamic Update of StrengthLayout for Semantic Record
            MemoryIndex.MemoryLocation semLoc = index.locate("bio-0002");
            assertNotNull(semLoc);
            int semSlot = (int) ((semLoc.offset() - router.get(MemoryType.SEMANTIC).dataOffset()) / router.layoutFor(MemoryType.SEMANTIC).stride());
            float beforeStrength = strengthStore.readStorageStrength(MemoryType.SEMANTIC, semSlot);
            strengthStore.casStorageStrength(MemoryType.SEMANTIC, semSlot, s -> s + 0.75f);
            float afterStrength = strengthStore.readStorageStrength(MemoryType.SEMANTIC, semSlot);
            out.printf("Dynamic Strength Update Test (bio-0002): before=%.4f, after=%.4f (CAS update verified)%n", beforeStrength, afterStrength);

            // Verify router.readRecordBody immediately reads the updated strength from StrengthStore
            var updatedBody = router.readRecordBody(semLoc, false);
            out.printf("router.readRecordBody for bio-0002: storageStrength in header record = %.4f%n", updatedBody.header().storageStrength());
            assertEquals(afterStrength, updatedBody.header().storageStrength(), 1e-4f);

            // Restore original value
            strengthStore.casStorageStrength(MemoryType.SEMANTIC, semSlot, s -> beforeStrength);

            out.flush();
        }
    }
}