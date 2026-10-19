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
package com.spectrayan.spector.commons.crypto;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DekCache} verifying bounded TTL, explicit zeroization on eviction/expiry,
 * and fail-closed behavior on KMS outage or key revocation (ADR-0088 §7, Issue #1070).
 */
@DisplayName("DekCache TTL, Zeroization & Fail-Closed Unit Tests")
class DekCacheTest {

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration delta) {
            this.current = this.current.plus(delta);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    @Test
    @DisplayName("Serves unwrapped DEK from cache within 5-minute TTL and re-unwraps after TTL expiry")
    void cacheHitWithinTtlAndReUnwrapAfterExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-18T12:00:00Z"));
        DekCache cache = new DekCache(Duration.ofMinutes(5), clock);

        AtomicInteger unwrapCalls = new AtomicInteger(0);
        byte[] rawDek = new byte[32];
        for (int i = 0; i < 32; i++) {
            rawDek[i] = (byte) (i + 10);
        }
        byte[] wrappedDek = new byte[]{1, 2, 3, 4};
        byte[] aad = EnvelopeCipher.buildNamespaceDekAad("acme", "ns-1", 1);

        KeyProvider provider = new KeyProvider() {
            @Override
            public String providerId() {
                return "test-kms";
            }

            @Override
            public WrappedKeyResult wrapDek(String tenantId, String kekKeyId, byte[] plaintextDek, byte[] aadBytes) {
                return new WrappedKeyResult(wrappedDek, new byte[12], kekKeyId, "v1");
            }

            @Override
            public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrapped, byte[] aadBytes) {
                unwrapCalls.incrementAndGet();
                return new DestroyableKeyMaterial(rawDek);
            }

            @Override
            public KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId) {
                return KeyHealthStatus.HEALTHY;
            }
        };

        try (DestroyableKeyMaterial first = cache.getOrUnwrap("acme", "ns-1", 1, "kek-1", wrappedDek, aad, provider)) {
            assertThat(first.bytes()).isEqualTo(rawDek);
            assertThat(unwrapCalls.get()).isEqualTo(1);
        }

        // Advance 4 minutes (within 5m TTL) -> cache hit, no KMS call
        clock.advance(Duration.ofMinutes(4));
        try (DestroyableKeyMaterial second = cache.getOrUnwrap("acme", "ns-1", 1, "kek-1", wrappedDek, aad, provider)) {
            assertThat(second.bytes()).isEqualTo(rawDek);
            assertThat(unwrapCalls.get()).isEqualTo(1);
        }

        // Advance another 2 minutes (total 6m > 5m TTL) -> cache expired, re-unwraps via KMS
        clock.advance(Duration.ofMinutes(2));
        try (DestroyableKeyMaterial third = cache.getOrUnwrap("acme", "ns-1", 1, "kek-1", wrappedDek, aad, provider)) {
            assertThat(third.bytes()).isEqualTo(rawDek);
            assertThat(unwrapCalls.get()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Fails closed with SPE-310-027 (KMS_UNAVAILABLE_FAIL_CLOSED) when KMS becomes unreachable after TTL")
    void failsClosedWhenKmsUnavailableAfterTtlExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-18T12:00:00Z"));
        DekCache cache = new DekCache(Duration.ofMinutes(5), clock);

        AtomicBoolean kmsHealthy = new AtomicBoolean(true);
        AtomicReference<DestroyableKeyMaterial> lastIssuedDek = new AtomicReference<>();
        byte[] wrappedDek = new byte[]{9, 8, 7, 6};
        byte[] aad = EnvelopeCipher.buildNamespaceDekAad("acme", "ns-prod", 1);

        KeyProvider provider = new KeyProvider() {
            @Override
            public String providerId() {
                return "aws-kms";
            }

            @Override
            public WrappedKeyResult wrapDek(String tenantId, String kekKeyId, byte[] plaintextDek, byte[] aadBytes) {
                return new WrappedKeyResult(wrappedDek, new byte[0], kekKeyId, "v1");
            }

            @Override
            public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrapped, byte[] aadBytes) {
                if (!kmsHealthy.get()) {
                    throw new RuntimeException("Connection timed out to kms.us-east-1.amazonaws.com");
                }
                DestroyableKeyMaterial mat = EnvelopeCipher.generateDek();
                lastIssuedDek.set(mat);
                return mat;
            }

            @Override
            public KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId) {
                return kmsHealthy.get() ? KeyHealthStatus.HEALTHY : KeyHealthStatus.UNREACHABLE;
            }
        };

        try (DestroyableKeyMaterial initial = cache.getOrUnwrap("acme", "ns-prod", 1, "arn:aws:kms:key/1", wrappedDek, aad, provider)) {
            assertThat(initial.isDestroyed()).isFalse();
        }

        // Simulate KMS outage and advance past 5-minute TTL
        kmsHealthy.set(false);
        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> cache.getOrUnwrap("acme", "ns-prod", 1, "arn:aws:kms:key/1", wrappedDek, aad, provider))
                .isInstanceOf(SpectorMemoryException.class)
                .satisfies(ex -> {
                    SpectorMemoryException sme = (SpectorMemoryException) ex;
                    assertThat(sme.errorCode()).isEqualTo(ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED);
                    assertThat(sme.codeId()).isEqualTo("SPE-310-027");
                    assertThat(sme.getMessage()).contains("acme").contains("arn:aws:kms:key/1");
                });

        // Expired cached DEK must have been purged and zeroized
        assertThat(cache.getIfPresent("acme", "ns-prod", 1)).isEmpty();
        assertThat(lastIssuedDek.get().isDestroyed()).isTrue();
        assertThat(lastIssuedDek.get().isZeroized()).isTrue();
    }

    @Test
    @DisplayName("Revoked KEK (SPE-820-010 / SPE-SEC-010) evicts and zeroizes all tenant DEKs immediately")
    void revokedKeyEvictsAndZeroizesAllTenantDeks() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-18T12:00:00Z"));
        DekCache cache = new DekCache(Duration.ofMinutes(5), clock);

        DestroyableKeyMaterial ns1Dek = EnvelopeCipher.generateDek();
        DestroyableKeyMaterial ns2Dek = EnvelopeCipher.generateDek();
        cache.put("acme", "ns-1", 1, ns1Dek);
        cache.put("acme", "ns-2", 1, ns2Dek);

        assertThat(cache.size()).isEqualTo(2);

        // Advance past TTL for ns-1, while ns-2 is re-inserted so it is still within TTL
        clock.advance(Duration.ofMinutes(4));
        cache.put("acme", "ns-2", 1, ns2Dek);
        clock.advance(Duration.ofMinutes(2)); // ns-1 is at 6m (expired), ns-2 is at 2m (active)

        KeyProvider revokedProvider = new KeyProvider() {
            @Override
            public String providerId() {
                return "aws-kms";
            }

            @Override
            public WrappedKeyResult wrapDek(String tenantId, String kekKeyId, byte[] plaintextDek, byte[] aadBytes) {
                throw new SpectorMemoryException(ErrorCode.BYOK_KEY_REVOKED, kekKeyId, tenantId);
            }

            @Override
            public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrapped, byte[] aadBytes) {
                throw new SpectorMemoryException(ErrorCode.BYOK_KEY_REVOKED, kekKeyId, tenantId);
            }

            @Override
            public KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId) {
                return KeyHealthStatus.REVOKED;
            }
        };

        assertThatThrownBy(() -> cache.getOrUnwrap(
                "acme", "ns-1", 1, "arn:aws:kms:key/revoked", new byte[]{1, 2}, new byte[]{3}, revokedProvider))
                .isInstanceOf(SpectorMemoryException.class)
                .satisfies(ex -> {
                    SpectorMemoryException sme = (SpectorMemoryException) ex;
                    assertThat(sme.errorCode()).isEqualTo(ErrorCode.BYOK_KEY_REVOKED);
                    assertThat(sme.codeId()).isEqualTo("SPE-820-010");
                });

        // Even ns-2 (which had not yet expired) must be evicted and zeroized because the tenant KEK is revoked
        assertThat(cache.getIfPresent("acme", "ns-2", 1)).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("invalidateTenant and invalidateNamespace explicitly zeroize cached DEKs while isolating other tenants")
    void invalidateTenantAndNamespaceZeroizeEntries() {
        DekCache cache = new DekCache(Duration.ofMinutes(5));
        try (DestroyableKeyMaterial acme1 = EnvelopeCipher.generateDek();
             DestroyableKeyMaterial acme2 = EnvelopeCipher.generateDek();
             DestroyableKeyMaterial globex1 = EnvelopeCipher.generateDek()) {

            cache.put("acme", "ns-1", 1, acme1);
            cache.put("acme", "ns-2", 1, acme2);
            cache.put("globex", "ns-1", 1, globex1);

            assertThat(cache.invalidateNamespace("acme", "ns-1")).isEqualTo(1);
            assertThat(cache.getIfPresent("acme", "ns-1", 1)).isEmpty();
            assertThat(cache.getIfPresent("acme", "ns-2", 1)).isPresent();

            assertThat(cache.invalidateTenant("acme")).isEqualTo(1);
            assertThat(cache.getIfPresent("acme", "ns-2", 1)).isEmpty();
            assertThat(cache.getIfPresent("globex", "ns-1", 1)).isPresent();
        } finally {
            cache.close();
        }
    }
}
