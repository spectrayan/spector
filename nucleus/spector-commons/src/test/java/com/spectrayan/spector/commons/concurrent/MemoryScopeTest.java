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
package com.spectrayan.spector.commons.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MemoryScope} — Java 25 ScopedValue carrier for session and namespace contexts.
 */
@DisplayName("MemoryScope")
class MemoryScopeTest {

    @Test
    @DisplayName("unbound scope returns null and false")
    void unboundScope() {
        assertThat(MemoryScope.sessionId()).isNull();
        assertThat(MemoryScope.namespaceId()).isNull();
        assertThat(MemoryScope.isSessionActive()).isFalse();
        assertThat(MemoryScope.isNamespaceActive()).isFalse();
        assertThat(MemoryScope.isActive()).isFalse();
    }

    @Test
    @DisplayName("runWithScope binds both session and namespace")
    void runWithBothScopes() {
        AtomicReference<String> seenSession = new AtomicReference<>();
        AtomicReference<String> seenNamespace = new AtomicReference<>();
        AtomicBoolean seenActive = new AtomicBoolean(false);

        MemoryScope.runWithScope("sess-123", "ns-456", () -> {
            seenSession.set(MemoryScope.sessionId());
            seenNamespace.set(MemoryScope.namespaceId());
            seenActive.set(MemoryScope.isSessionActive() && MemoryScope.isNamespaceActive());
        });

        assertThat(seenSession.get()).isEqualTo("sess-123");
        assertThat(seenNamespace.get()).isEqualTo("ns-456");
        assertThat(seenActive.get()).isTrue();

        // Ensure unbound after exit
        assertThat(MemoryScope.sessionId()).isNull();
        assertThat(MemoryScope.namespaceId()).isNull();
    }

    @Test
    @DisplayName("callWithScope binds both session and namespace and returns value")
    void callWithBothScopes() throws Exception {
        String result = MemoryScope.callWithScope("sess-abc", "ns-xyz", () -> {
            assertThat(MemoryScope.sessionId()).isEqualTo("sess-abc");
            assertThat(MemoryScope.namespaceId()).isEqualTo("ns-xyz");
            return MemoryScope.sessionId() + ":" + MemoryScope.namespaceId();
        });

        assertThat(result).isEqualTo("sess-abc:ns-xyz");
        assertThat(MemoryScope.sessionId()).isNull();
        assertThat(MemoryScope.namespaceId()).isNull();
    }

    @Test
    @DisplayName("runWithScope handles nullable or blank parameters gracefully")
    void runWithPartialScope() {
        AtomicReference<String> seenSession = new AtomicReference<>();
        AtomicReference<String> seenNamespace = new AtomicReference<>();

        // Only session
        MemoryScope.runWithScope("sess-only", null, () -> {
            seenSession.set(MemoryScope.sessionId());
            seenNamespace.set(MemoryScope.namespaceId());
        });
        assertThat(seenSession.get()).isEqualTo("sess-only");
        assertThat(seenNamespace.get()).isNull();

        // Only namespace
        MemoryScope.runWithScope(null, "ns-only", () -> {
            seenSession.set(MemoryScope.sessionId());
            seenNamespace.set(MemoryScope.namespaceId());
        });
        assertThat(seenSession.get()).isNull();
        assertThat(seenNamespace.get()).isEqualTo("ns-only");

        // Neither
        MemoryScope.runWithScope("", "  ", () -> {
            seenSession.set(MemoryScope.sessionId());
            seenNamespace.set(MemoryScope.namespaceId());
        });
        assertThat(seenSession.get()).isNull();
        assertThat(seenNamespace.get()).isNull();
    }

    @Test
    @DisplayName("ConcurrentTasks fireAndForget propagates scope to virtual thread")
    void concurrentTasksFireAndForgetScope() throws InterruptedException {
        AtomicReference<String> seenSession = new AtomicReference<>();
        AtomicReference<String> seenNamespace = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean(false);

        ConcurrentTasks.fireAndForget("sess-async", "ns-async", () -> {
            seenSession.set(MemoryScope.sessionId());
            seenNamespace.set(MemoryScope.namespaceId());
            done.set(true);
        });

        long deadline = System.currentTimeMillis() + 3000;
        while (!done.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertThat(done.get()).isTrue();
        assertThat(seenSession.get()).isEqualTo("sess-async");
        assertThat(seenNamespace.get()).isEqualTo("ns-async");
    }
}
