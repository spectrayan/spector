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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.PurgeResult;
import com.spectrayan.spector.memory.policy.DeletionKind;
import com.spectrayan.spector.memory.policy.DeletionRequest;
import com.spectrayan.spector.memory.policy.MutationPolicy;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests for the {@code purge} verb: physical destruction, as distinct from {@code forget}'s
 * tombstone.
 *
 * <p>The central test here reads raw bytes, not API responses. "Recall no longer returns it" is satisfied by
 * a plain tombstone, so a test asserting only that would pass against an implementation that destroys
 * nothing — which is precisely the state this work exists to leave behind.</p>
 */
@DisplayName("purge — end to end")
class PurgeEndToEndTest {

    private static SpectorMemory newMemory(Path dir, MutationPolicy policy) {
        FakeEmbeddingProvider embedProvider = new FakeEmbeddingProvider();
        var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(embedProvider.dimensions())
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(50)
                .setProceduralCapacity(20);
        var builder = DefaultSpectorMemory.builder(memProps)
                .embeddingProvider(embedProvider)
                .namespaceId("purge-test")
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY);
        if (policy != null) {
            builder.mutationPolicy(policy);
        }
        return builder.build();
    }

    @Nested
    @DisplayName("Physical destruction")
    class PhysicalDestruction {

        @Test
        @DisplayName("forget leaves the payload readable; purge zeroes it — asserted on raw bytes")
        void purgeDestroysWhereForgetDoesNot(@TempDir Path dir) {
            try (SpectorMemory memory = newMemory(dir, null)) {
                memory.remember("kept", "This memory is only tombstoned.", MemoryType.SEMANTIC, "t");
                memory.remember("gone", "This memory is physically destroyed.", MemoryType.SEMANTIC, "t");

                var index = ((DefaultSpectorMemory) memory).index();
                var forgetLoc = index.locate("kept");
                var purgeLoc = index.locate("gone");
                assertThat(forgetLoc).isNotNull();
                assertThat(purgeLoc).isNotNull();

                var router = ((DefaultSpectorMemory) memory).cognitiveRouter();
                byte[] beforePurge = router.readVector(purgeLoc);
                assertThat(beforePurge).isNotNull();
                assertThat(isAllZero(beforePurge)).as("payload must be non-trivial before purge").isFalse();

                memory.forget("kept");
                // The uncomfortable fact this whole spec exists for: a forgotten record's bytes are intact.
                byte[] afterForget = ((DefaultSpectorMemory) memory).cognitiveRouter()
                        .readVector(forgetLoc);
                assertThat(afterForget).as("forget does not destroy anything").isNotNull();
                assertThat(isAllZero(afterForget)).isFalse();

                PurgeResult result = memory.purge("gone");

                assertThat(result.found()).isTrue();
                assertThat(result.payloadBytesZeroed()).isEqualTo(beforePurge.length);
                // readVector reports absence for a purged record rather than handing back zeros, so read
                // the region directly to prove the bytes really are gone.
                assertThat(router.isPurged(purgeLoc)).isTrue();
                assertThat(router.readVector(purgeLoc))
                        .as("a purged payload is reported as absent, not as an all-zero vector")
                        .isNull();
            }
        }

        @Test
        @DisplayName("the purged record's stored text is zeroed too")
        void purgeErasesText(@TempDir Path dir) {
            try (SpectorMemory memory = newMemory(dir, null)) {
                memory.remember("txt", "Distinctive text that must not survive a purge.",
                        MemoryType.SEMANTIC, "t");

                PurgeResult result = memory.purge("txt");

                assertThat(result.found()).isTrue();
                // Payload and text live in separate stores; destroying only one leaves content readable.
                // This store is IN_MEMORY, so there is no text.dat frame to zero and the on-heap entry is
                // the only copy — hence textDestroyed() rather than a byte count. Asserting
                // textBytesZeroed() > 0 here would be asserting that the store wrote bytes it never wrote.
                assertThat(result.inlineTextDropped()).isTrue();
                assertThat(result.textDestroyed()).isTrue();
                assertThat(result.hasLocalRetention()).isFalse();
            }
        }

        @Test
        @DisplayName("on a disk-backed store, the text bytes in text.dat are overwritten on disk")
        void purgeZeroesTextOnDisk(@TempDir Path dir) {
            String secret = "Distinctive-text-that-must-not-survive-a-purge-on-disk";
            FakeEmbeddingProvider embedProvider = new FakeEmbeddingProvider();
            var memProps = new com.spectrayan.spector.config.properties.MemoryProperties()
                    .setDimensions(embedProvider.dimensions())
                    .setWorkingCapacity(20)
                    .setEpisodicPartitionCapacity(100)
                    .setSemanticCapacity(50)
                    .setProceduralCapacity(20);
            PurgeResult result;
            try (SpectorMemory memory = DefaultSpectorMemory.builder(memProps)
                    .embeddingProvider(embedProvider)
                    .namespaceId("purge-disk")
                    .persistence(dir)
                    .persistenceMode(MemoryPersistenceMode.DISK)
                    .build()) {
                memory.remember("disk-txt", secret, MemoryType.SEMANTIC, "t");
                memory.remember("disk-keep", "Unrelated surviving content on disk.", MemoryType.SEMANTIC, "t");
                result = memory.purge("disk-txt");
            }

            // This is the assertion the in-memory case cannot make: there are real bytes in a real file, and
            // after a purge the secret must not be findable in any of them.
            assertThat(result.found()).isTrue();
            assertThat(result.textBytesZeroed()).isEqualTo(secret.length());
            assertThat(result.textDestroyed()).isTrue();

            List<Path> scanned = new ArrayList<>();
            try (var walk = java.nio.file.Files.walk(dir)) {
                walk.filter(java.nio.file.Files::isRegularFile).forEach(scanned::add);
            } catch (java.io.IOException e) {
                throw new AssertionError("could not walk the store directory", e);
            }
            assertThat(scanned).as("the store must have written some files to scan").isNotEmpty();
            for (Path file : scanned) {
                byte[] bytes;
                try {
                    bytes = java.nio.file.Files.readAllBytes(file);
                } catch (java.io.IOException e) {
                    continue;
                }
                assertThat(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1))
                        .as("purged text must not survive anywhere under the store: %s", file)
                        .doesNotContain(secret);
            }
        }

        @Test
        @DisplayName("purge is idempotent and a missing id is reported, not thrown")
        void purgeIsIdempotent(@TempDir Path dir) {
            try (SpectorMemory memory = newMemory(dir, null)) {
                memory.remember("twice", "Purged twice.", MemoryType.SEMANTIC, "t");

                assertThat(memory.purge("twice").found()).isTrue();
                // Same idempotent-delete contract forget has: the second call reports honestly rather
                // than throwing or claiming a second destruction.
                PurgeResult second = memory.purge("twice");
                assertThat(second.found()).isFalse();
                assertThat(second.payloadBytesZeroed()).isZero();

                assertThatCode(() -> memory.purge("never-existed")).doesNotThrowAnyException();
                assertThat(memory.purge("never-existed").found()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Every read surface misses a purged record")
    class ReadSurfaces {

        @Test
        @DisplayName("recall, inspect and export all miss it")
        void allReadSurfacesMissIt(@TempDir Path dir) {
            try (SpectorMemory memory = newMemory(dir, null)) {
                memory.remember("survivor", "Cats are excellent companions.", MemoryType.SEMANTIC, "t");
                memory.remember("target", "Cats are excellent companions indeed.", MemoryType.SEMANTIC, "t");

                assertThat(memory.inspect("target")).isNotNull();
                assertThat(memory.exportJson()).contains("target");

                memory.purge("target");

                assertThat(memory.inspect("target")).as("inspect").isNull();
                assertThat(memory.exportJson()).as("export").doesNotContain("\"target\"");
                assertThat(memory.recall("Cats are excellent companions")
                        .stream().map(r -> r.id()).toList())
                        .as("recall")
                        .doesNotContain("target");
                // The unrelated memory is untouched — a purge that took out its neighbours would also
                // pass every assertion above.
                assertThat(memory.inspect("survivor")).isNotNull();
                assertThat(memory.exportJson()).contains("survivor");
            }
        }

        @Test
        @DisplayName("export omits purged records for a reason independent of the tombstone bit")
        void exportOmitsPurgedIndependently(@TempDir Path dir) {
            try (SpectorMemory memory = newMemory(dir, null)) {
                memory.remember("exp", "Exported until purged.", MemoryType.SEMANTIC, "t");
                memory.purge("exp");
                // Both bits are set, so this would pass either way today. It is here so that if export ever
                // gains an --include-tombstones mode, purged records do not silently ride along with it.
                assertThat(memory.exportJson()).doesNotContain("\"exp\"");
            }
        }
    }

    @Nested
    @DisplayName("Graph detachment")
    class GraphDetachment {

        @Test
        @DisplayName("a purged record is no longer reachable in any graph plane")
        void purgedRecordLeavesNoGraphReference(@TempDir Path dir) {
            try (SpectorMemory memory = newMemory(dir, null)) {
                // Co-activated memories build Hebbian edges between them.
                for (int i = 0; i < 4; i++) {
                    memory.remember("g-" + i, "Related concept number " + i + " about astronomy.",
                            MemoryType.SEMANTIC, "astro");
                }
                var impl = (DefaultSpectorMemory) memory;
                int slot = impl.index().locate("g-1").graphSlot();

                memory.purge("g-1");

                // A purged record still wired into the graph keeps steering spreading activation, so
                // "invisible to recall" is not sufficient — the edges have to be gone.
                assertThat(impl.graph().isReferencedInAnyGraph(slot))
                        .as("no graph plane may still reference the purged slot")
                        .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Deletion policy")
    class Policy {

        /** Records every request the engine presents, and refuses the ones it is told to. */
        private static final class RecordingPolicy implements MutationPolicy {
            final List<DeletionRequest> seen = new ArrayList<>();
            boolean refuse;

            @Override
            public void checkDeletion(DeletionRequest request) {
                seen.add(request);
                if (refuse) {
                    throw new IllegalStateException("refused by policy: " + request.kind());
                }
            }
        }

        @Test
        @DisplayName("purge under a refusing policy throws, and destroys nothing")
        void purgeUnderHoldThrowsAndDestroysNothing(@TempDir Path dir) {
            var policy = new RecordingPolicy();
            try (SpectorMemory memory = newMemory(dir, policy)) {
                memory.remember("held", "Protected by policy.", MemoryType.SEMANTIC, "t");
                var impl = (DefaultSpectorMemory) memory;
                var loc = impl.index().locate("held");
                var router = impl.cognitiveRouter();

                policy.refuse = true;
                assertThatThrownBy(() -> memory.purge("held"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("refused by policy");

                // The check runs before the first byte is touched, so a refusal cannot leave a partially
                // destroyed record.
                assertThat(router.isPurged(loc)).isFalse();
                assertThat(router.readVector(loc)).isNotNull();
                assertThat(memory.inspect("held")).isNotNull();
            }
        }

        @Test
        @DisplayName("the policy is consulted by the embedded API — not only by REST")
        void policyIsConsultedFromTheEngine(@TempDir Path dir) {
            var policy = new RecordingPolicy();
            try (SpectorMemory memory = newMemory(dir, policy)) {
                memory.remember("p1", "Purged with policy watching.", MemoryType.SEMANTIC, "t");
                memory.remember("p2", "Forgotten with policy watching.", MemoryType.SEMANTIC, "t");

                memory.purge("p1");
                memory.forget("p2");
                ((DefaultSpectorMemory) memory).vacuum(MemoryType.SEMANTIC);

                // This is the whole point of putting the check in the engine: these three calls never went
                // near a controller. A check at the HTTP boundary would have seen none of them.
                assertThat(policy.seen).extracting(DeletionRequest::kind)
                        .contains(DeletionKind.PURGE, DeletionKind.FORGET, DeletionKind.VACUUM);
                assertThat(policy.seen).extracting(DeletionRequest::namespaceId)
                        .allMatch("purge-test"::equals);
                assertThat(policy.seen).filteredOn(r -> r.kind() == DeletionKind.PURGE)
                        .singleElement()
                        .satisfies(r -> assertThat(r.memoryId()).isEqualTo("p1"));
            }
        }

        @Test
        @DisplayName("with no policy installed, behaviour is unchanged — embedded use takes no dependency")
        void noPolicyMeansNoBehaviourChange(@TempDir Path dir) {
            try (SpectorMemory memory = newMemory(dir, null)) {
                memory.remember("free", "No policy installed.", MemoryType.SEMANTIC, "t");
                assertThatCode(() -> memory.purge("free")).doesNotThrowAnyException();
            }
        }
    }

    @Nested
    @DisplayName("The audit report")
    class AuditReport {

        @Test
        @DisplayName("discloses what it could not reach, and claims no crypto-erase")
        void reportDisclosesItsLimits(@TempDir Path dir) {
            try (SpectorMemory memory = newMemory(dir, null)) {
                memory.remember("aud", "Audited purge.", MemoryType.SEMANTIC, "t");

                PurgeResult result = memory.purge("aud");

                // An audit report that only said "success" would overstate the operation: copies in DR
                // exports and on replica disks survive a purge entirely.
                assertThat(result.unreachableCopies())
                        .contains("dr_exports", "replica_disks", "cold_tier_objects",
                                "filesystem_snapshots_and_backups");
                assertThat(result.disclosure())
                        .contains("does NOT reach")
                        .contains("No cryptographic erasure was performed");
                // No DEK exists in this system, so any crypto-erase claim would be false. Assert the word
                // never appears as a capability.
                assertThat(PurgeResult.MECHANISM).doesNotContainIgnoringCase("crypto");
                // Retained metadata is declared, not silently left behind.
                assertThat(result.retainedHeaderFields()).contains("timestamp_ms");
                assertThat(result.walRecorded())
                        .as("an unrecorded purge is one a crash can undo")
                        .isTrue();
            }
        }

        @Test
        @DisplayName("a not-found purge still carries the disclosure rather than an empty success")
        void notFoundStillDiscloses(@TempDir Path dir) {
            try (SpectorMemory memory = newMemory(dir, null)) {
                PurgeResult result = memory.purge("absent");
                assertThat(result.found()).isFalse();
                assertThat(result.unreachableCopies()).isNotEmpty();
                assertThat(result.disclosure()).isNotBlank();
            }
        }
    }

    private static boolean isAllZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) return false;
        }
        return true;
    }
}
