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
package com.spectrayan.spector.memory.policy;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.error.SpectorServerException;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.SpectorMemory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EngineFencingIntegrationTest {

    @Test
    void engineRefusesWritesWhenEpochIsStale() throws Exception {
        AtomicLong currentEpoch = new AtomicLong(10);
        FencingMutationPolicy policy = new FencingMutationPolicy(currentEpoch::get);

        SpectorMemory memory = SpectorMemoryBuilder.create()
                .namespaceId("test-ns")
                .mutationPolicy(policy)
                .embeddingProvider(new com.spectrayan.spector.memory.test.FakeEmbeddingProvider())
                .build();

        // Should succeed with valid epoch
        MemoryScope.callWithScope("session", "test-ns", () -> {
            ScopedValue.where(MemoryScope.FENCE_EPOCH, 10L).run(() -> {
                assertDoesNotThrow(() -> memory.remember("id1", "Hello", MemoryType.SEMANTIC, MemorySource.OBSERVED));
            });
            return null;
        });

        // Should fail with stale epoch
        MemoryScope.callWithScope("session", "test-ns", () -> {
            ScopedValue.where(MemoryScope.FENCE_EPOCH, 9L).run(() -> {
                assertThrows(SpectorServerException.class, () -> 
                    memory.remember("id2", "World", MemoryType.SEMANTIC, MemorySource.OBSERVED)
                );
            });
            return null;
        });
    }
}
