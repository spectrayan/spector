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
package com.spectrayan.spector.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

/**
 * Tests for {@link ProviderFingerprint}.
 *
 * <p>The fingerprint decides which namespaces share one provider instance. Over-sharing is a
 * correctness bug — two namespaces silently using the wrong model or the wrong account. Under-sharing
 * is a resource bug — one HTTP client or ONNX session per namespace. Each test below pins one side of
 * that boundary.</p>
 */
@DisplayName("Provider Fingerprint")
class ProviderFingerprintTest {

    private static final String SECRET = "fixture-credential-alpha";

    private static ProviderConfig config(String model, String apiKey, Map<String, String> properties) {
        return new ProviderConfig("ollama", "ollama", model, apiKey,
                "http://localhost:11434", 768, properties);
    }

    @Nested
    @DisplayName("sharing (must be equal)")
    class Sharing {

        @Test
        @DisplayName("identical configuration yields equal fingerprints and equal digests")
        void identicalConfigurationIsEqual() {
            var a = ProviderFingerprint.of(config("nomic-embed-text", SECRET, Map.of("timeout", "30")));
            var b = ProviderFingerprint.of(config("nomic-embed-text", SECRET, Map.of("timeout", "30")));

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a.digest()).isEqualTo(b.digest());
        }

        @Test
        @DisplayName("properties map iteration order does not change the fingerprint")
        void propertyOrderIsIrrelevant() {
            var first = new LinkedHashMap<String, String>();
            first.put("timeout", "30");
            first.put("batchSize", "32");
            var second = new LinkedHashMap<String, String>();
            second.put("batchSize", "32");
            second.put("timeout", "30");

            assertThat(ProviderFingerprint.of(config("m", "", first)))
                    .isEqualTo(ProviderFingerprint.of(config("m", "", second)));
        }

        @Test
        @DisplayName("null and empty properties are the same absence")
        void nullAndEmptyPropertiesAgree() {
            assertThat(ProviderFingerprint.of(config("m", "", Map.of())).propertiesHash())
                    .isEqualTo(ProviderFingerprint.NO_PROPERTIES);
            assertThat(ProviderFingerprint.of(config("m", "", null)).propertiesHash())
                    .isEqualTo(ProviderFingerprint.NO_PROPERTIES);
        }
    }

    @Nested
    @DisplayName("splitting (must differ)")
    class Splitting {

        @Test
        @DisplayName("a different model splits the pool")
        void modelSplits() {
            assertThat(ProviderFingerprint.of(config("nomic-embed-text", SECRET, Map.of())))
                    .isNotEqualTo(ProviderFingerprint.of(config("mxbai-embed-large", SECRET, Map.of())));
        }

        @Test
        @DisplayName("a different API key splits the pool — two accounts must not share one client")
        void apiKeySplits() {
            var a = ProviderFingerprint.of(config("nomic-embed-text", "fixture-credential-tenant-a", Map.of()));
            var b = ProviderFingerprint.of(config("nomic-embed-text", "fixture-credential-tenant-b", Map.of()));

            assertThat(a).isNotEqualTo(b);
            assertThat(a.secretDigest()).isNotEqualTo(b.secretDigest());
        }

        @Test
        @DisplayName("a different modelPath splits the pool — two ONNX files are two models")
        void modelPathSplits() {
            assertThat(ProviderFingerprint.of(config("m", "", Map.of("modelPath", "/models/a.onnx"))))
                    .isNotEqualTo(ProviderFingerprint.of(config("m", "", Map.of("modelPath", "/models/b.onnx"))));
        }

        @Test
        @DisplayName("a different executionProvider splits the pool")
        void executionProviderSplits() {
            assertThat(ProviderFingerprint.of(config("m", "", Map.of("executionProvider", "CPU"))))
                    .isNotEqualTo(ProviderFingerprint.of(config("m", "", Map.of("executionProvider", "CUDA"))));
        }

        @Test
        @DisplayName("a different dimensionality splits the pool")
        void dimensionsSplit() {
            var small = new ProviderConfig("openai", "openai", "text-embedding-3-small",
                    SECRET, "", 512, Map.of());
            var large = new ProviderConfig("openai", "openai", "text-embedding-3-small",
                    SECRET, "", 1536, Map.of());

            assertThat(ProviderFingerprint.of(small)).isNotEqualTo(ProviderFingerprint.of(large));
        }

        @Test
        @DisplayName("keys and values cannot be confused across the property boundary")
        void lengthPrefixingPreventsPropertyCollisions() {
            assertThat(ProviderFingerprint.of(config("m", "", Map.of("ab", "c"))))
                    .isNotEqualTo(ProviderFingerprint.of(config("m", "", Map.of("a", "bc"))));
        }
    }

    @Nested
    @DisplayName("secret handling")
    class SecretHandling {

        @Test
        @DisplayName("the raw API key appears in no part of the fingerprint")
        void rawKeyNeverLeaks() {
            var fingerprint = ProviderFingerprint.of(config("nomic-embed-text", SECRET, Map.of()));

            assertThat(fingerprint.secretDigest()).doesNotContain(SECRET);
            assertThat(fingerprint.toString()).doesNotContain(SECRET);
            assertThat(fingerprint.toLogString()).doesNotContain(SECRET);
            assertThat(fingerprint.digest()).doesNotContain(SECRET);
        }

        @Test
        @DisplayName("an absent key is a legible sentinel, not a digest of the empty string")
        void absentKeyUsesSentinel() {
            assertThat(ProviderFingerprint.of(config("m", "", Map.of())).secretDigest())
                    .isEqualTo(ProviderFingerprint.NO_SECRET);
            assertThat(ProviderFingerprint.of(config("m", null, Map.of())).secretDigest())
                    .isEqualTo(ProviderFingerprint.NO_SECRET);
            assertThat(ProviderFingerprint.of(config("m", "   ", Map.of())).secretDigest())
                    .isEqualTo(ProviderFingerprint.NO_SECRET);
        }

        @Test
        @DisplayName("a configured key is distinguishable from no key at all")
        void configuredKeyDiffersFromAbsence() {
            assertThat(ProviderFingerprint.of(config("m", SECRET, Map.of())))
                    .isNotEqualTo(ProviderFingerprint.of(config("m", "", Map.of())));
        }
    }

    @Nested
    @DisplayName("digest()")
    class Digest {

        @Test
        @DisplayName("is 32 lowercase hex characters")
        void shapeIsStable() {
            assertThat(ProviderFingerprint.of(config("nomic-embed-text", SECRET, Map.of())).digest())
                    .hasSize(32)
                    .matches("[0-9a-f]{32}");
        }

        @Test
        @DisplayName("differs whenever the fingerprint differs")
        void tracksEveryComponent() {
            var base = ProviderFingerprint.of(config("nomic-embed-text", SECRET, Map.of()));

            assertThat(base.digest())
                    .isNotEqualTo(ProviderFingerprint.of(config("other-model", SECRET, Map.of())).digest())
                    .isNotEqualTo(ProviderFingerprint.of(config("nomic-embed-text", "fixture-credential-other", Map.of())).digest())
                    .isNotEqualTo(ProviderFingerprint.of(config("nomic-embed-text", SECRET, Map.of("k", "v"))).digest());
        }
    }

    @Nested
    @DisplayName("ofModel()")
    class OfModel {

        @Test
        @DisplayName("distinguishes models and dimensionalities")
        void distinguishesModelAndDimensions() {
            assertThat(ProviderFingerprint.ofModel("a", 768))
                    .isNotEqualTo(ProviderFingerprint.ofModel("b", 768))
                    .isNotEqualTo(ProviderFingerprint.ofModel("a", 384));
            assertThat(ProviderFingerprint.ofModel("a", 768)).isEqualTo(ProviderFingerprint.ofModel("a", 768));
        }

        @Test
        @DisplayName("an unknown model name is recorded as unknown rather than blank")
        void unknownModelIsNamed() {
            assertThat(ProviderFingerprint.ofModel(null, 768).model()).isEqualTo("unknown");
            assertThat(ProviderFingerprint.ofModel("  ", 768).model()).isEqualTo("unknown");
        }

        @Test
        @DisplayName("negative dimensions are clamped rather than rejected")
        void negativeDimensionsAreClamped() {
            assertThat(ProviderFingerprint.ofModel("a", -1).dimensions()).isZero();
        }
    }

    @Nested
    @DisplayName("ofProvider()")
    class OfProvider {

        /** Stands in for an embedding provider whose backend is unreachable. */
        private EmbeddingProvider offlineProvider(boolean modelNameThrows) {
            return new EmbeddingProvider() {
                @Override
                public EmbeddingResult embed(String text) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.util.List<EmbeddingResult> embedBatch(java.util.List<String> texts) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int dimensions() {
                    throw new IllegalStateException("Connection refused (provider offline)");
                }

                @Override
                public String modelName() {
                    if (modelNameThrows) {
                        throw new IllegalStateException("Connection refused (provider offline)");
                    }
                    return "offline-embed";
                }

                @Override
                public int maxTokens() {
                    return 0;
                }

                @Override
                public void close() {
                    // no resources
                }
            };
        }

        @Test
        @DisplayName("a provider whose dimensions() throws still yields a fingerprint")
        void toleratesThrowingDimensions() {
            var fingerprint = ProviderFingerprint.ofProvider(offlineProvider(false));

            assertThat(fingerprint).isNotNull();
            assertThat(fingerprint.model()).isEqualTo("offline-embed");
            assertThat(fingerprint.dimensions()).isZero();
        }

        @Test
        @DisplayName("a provider whose modelName() also throws degrades to the unknown scope")
        void toleratesThrowingModelName() {
            var fingerprint = ProviderFingerprint.ofProvider(offlineProvider(true));

            assertThat(fingerprint).isNotNull();
            assertThat(fingerprint.model()).isEqualTo("unknown");
        }

        @Test
        @DisplayName("an anonymous provider never shares a scope with an identified one")
        void unknownScopeIsDistinct() {
            assertThat(ProviderFingerprint.ofProvider(offlineProvider(true)))
                    .isNotEqualTo(ProviderFingerprint.ofProvider(offlineProvider(false)));
        }

        @Test
        @DisplayName("a null provider yields no fingerprint rather than throwing")
        void nullProviderYieldsNull() {
            assertThat(ProviderFingerprint.ofProvider(null)).isNull();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects a null name or type")
        void rejectsNullIdentity() {
            assertThatThrownBy(() -> new ProviderFingerprint(
                    null, "t", "m", "", 1, "p", "s"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ProviderFingerprint(
                    "n", null, "m", "", 1, "p", "s"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects negative dimensions")
        void rejectsNegativeDimensions() {
            assertThatThrownBy(() -> new ProviderFingerprint(
                    "n", "t", "m", "", -1, "p", "s"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Dimensions must be >= 0");
        }

        @Test
        @DisplayName("normalises null model and baseUrl to empty, matching ProviderConfig")
        void normalisesNulls() {
            var fingerprint = new ProviderFingerprint("n", "t", null, null, 0, null, null);

            assertThat(fingerprint.model()).isEmpty();
            assertThat(fingerprint.baseUrl()).isEmpty();
            assertThat(fingerprint.propertiesHash()).isEqualTo(ProviderFingerprint.NO_PROPERTIES);
            assertThat(fingerprint.secretDigest()).isEqualTo(ProviderFingerprint.NO_SECRET);
        }
    }
}
