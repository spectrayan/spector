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
package com.spectrayan.spector.synapse.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFingerprint;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

/**
 * Tests for {@link EmbeddingProviderPool}.
 *
 * <p>The pool exists to make provider count proportional to distinct configurations rather than to
 * namespace count. Both halves of that need pinning: identical configurations must share one instance,
 * and a provider must never be closed while a namespace still holds it.</p>
 */
@DisplayName("Embedding Provider Pool")
class EmbeddingProviderPoolTest {

    /** Records whether it was closed, so lifecycle claims can be asserted rather than assumed. */
    static final class TrackedProvider implements EmbeddingProvider {
        private final String model;
        boolean closed;

        TrackedProvider(String model) {
            this.model = model;
        }

        @Override
        public EmbeddingResult embed(String text) {
            return new EmbeddingResult(new float[]{1.0f}, 1, model);
        }

        @Override
        public List<EmbeddingResult> embedBatch(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public int dimensions() {
            return 1;
        }

        @Override
        public String modelName() {
            return model;
        }

        @Override
        public int maxTokens() {
            return 8192;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static ProviderFingerprint fingerprint(String model) {
        return ProviderFingerprint.of(new ProviderConfig(
                "ollama", "ollama", model, "", "http://localhost:11434", 768, Map.of()));
    }

    @Test
    @DisplayName("identical configurations share one provider instance")
    void identicalConfigurationsShareOneInstance() {
        var pool = new EmbeddingProviderPool();
        var creations = new AtomicInteger();
        var fp = fingerprint("nomic-embed-text");

        EmbeddingProvider first = pool.acquire(fp, f -> {
            creations.incrementAndGet();
            return new TrackedProvider("nomic-embed-text");
        });
        EmbeddingProvider second = pool.acquire(fp, f -> {
            creations.incrementAndGet();
            return new TrackedProvider("nomic-embed-text");
        });

        // Reference equality is the assertion that distinguishes this design from a per-namespace one.
        assertThat(second).isSameAs(first);
        assertThat(creations.get()).isEqualTo(1);
        assertThat(pool.distinctConfigurations()).isEqualTo(1);
        assertThat(pool.referenceCount(fp)).isEqualTo(2);
        pool.close();
    }

    @Test
    @DisplayName("different configurations get their own provider")
    void differentConfigurationsAreSeparate() {
        var pool = new EmbeddingProviderPool();

        EmbeddingProvider a = pool.acquire(fingerprint("model-a"), f -> new TrackedProvider("model-a"));
        EmbeddingProvider b = pool.acquire(fingerprint("model-b"), f -> new TrackedProvider("model-b"));

        assertThat(b).isNotSameAs(a);
        assertThat(pool.distinctConfigurations()).isEqualTo(2);
        pool.close();
    }

    @Test
    @DisplayName("the pool does not grow with namespace count when configuration is shared")
    void poolDoesNotGrowWithNamespaceCount() {
        var pool = new EmbeddingProviderPool();
        var fp = fingerprint("nomic-embed-text");

        for (int i = 0; i < 50; i++) {
            pool.acquire(fp, f -> new TrackedProvider("nomic-embed-text"));
        }

        assertThat(pool.distinctConfigurations()).isEqualTo(1);
        assertThat(pool.referenceCount(fp)).isEqualTo(50);
        pool.close();
    }

    @Test
    @DisplayName("a provider is not closed while another namespace still holds it")
    void releaseDoesNotCloseAReferencedProvider() {
        var pool = new EmbeddingProviderPool();
        var fp = fingerprint("nomic-embed-text");
        var tracked = new TrackedProvider("nomic-embed-text");

        pool.acquire(fp, f -> tracked);
        pool.acquire(fp, f -> tracked);

        assertThat(pool.release(fp)).isFalse();
        assertThat(tracked.closed).isFalse();
        assertThat(pool.referenceCount(fp)).isEqualTo(1);

        assertThat(pool.release(fp)).isTrue();
        assertThat(tracked.closed).isTrue();
        assertThat(pool.distinctConfigurations()).isZero();
        pool.close();
    }

    @Test
    @DisplayName("releasing an unknown fingerprint is a no-op, not an error")
    void releasingUnknownIsNoOp() {
        var pool = new EmbeddingProviderPool();

        assertThat(pool.release(fingerprint("never-acquired"))).isFalse();
        assertThat(pool.release(null)).isFalse();
        pool.close();
    }

    @Test
    @DisplayName("past the cap the pool refuses rather than evicting something in use")
    void refusesInsteadOfEvicting() {
        var pool = new EmbeddingProviderPool(2);
        var first = new TrackedProvider("model-a");
        pool.acquire(fingerprint("model-a"), f -> first);
        pool.acquire(fingerprint("model-b"), f -> new TrackedProvider("model-b"));

        assertThatThrownBy(() -> pool.acquire(fingerprint("model-c"), f -> new TrackedProvider("model-c")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2")
                .hasMessageContaining("maximum");

        // The critical part: nothing already in use was closed to make room.
        assertThat(first.closed).isFalse();
        assertThat(pool.distinctConfigurations()).isEqualTo(2);
        pool.close();
    }

    @Test
    @DisplayName("releasing below the cap admits a new configuration again")
    void releasingFreesCapacity() {
        var pool = new EmbeddingProviderPool(1);
        pool.acquire(fingerprint("model-a"), f -> new TrackedProvider("model-a"));
        pool.release(fingerprint("model-a"));

        var admitted = pool.acquire(fingerprint("model-b"), f -> new TrackedProvider("model-b"));

        assertThat(admitted).isNotNull();
        assertThat(pool.distinctConfigurations()).isEqualTo(1);
        pool.close();
    }

    @Test
    @DisplayName("close() closes every pooled provider and refuses further acquisition")
    void closeReleasesEverything() {
        var pool = new EmbeddingProviderPool();
        var a = new TrackedProvider("model-a");
        var b = new TrackedProvider("model-b");
        pool.acquire(fingerprint("model-a"), f -> a);
        pool.acquire(fingerprint("model-b"), f -> b);

        pool.close();

        assertThat(a.closed).isTrue();
        assertThat(b.closed).isTrue();
        assertThat(pool.distinctConfigurations()).isZero();
        assertThatThrownBy(() -> pool.acquire(fingerprint("model-c"), f -> new TrackedProvider("model-c")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("a factory returning null is rejected rather than pooled")
    void nullFromFactoryIsRejected() {
        var pool = new EmbeddingProviderPool();

        assertThatThrownBy(() -> pool.acquire(fingerprint("model-a"), f -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null");
        assertThat(pool.distinctConfigurations()).isZero();
        pool.close();
    }
}
