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
package com.spectrayan.spector.synapse.security.kms;

import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.EnvelopeCipher;
import com.spectrayan.spector.commons.crypto.KeyHealthStatus;
import com.spectrayan.spector.commons.crypto.KeyProvider;
import com.spectrayan.spector.commons.crypto.WrappedKeyResult;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local file / dev-test {@link KeyProvider} ({@code local-file}) that wraps and unwraps DEKs
 * using a local 256-bit AES-GCM KEK (ADR-0088 §5.2, Issue #1070).
 *
 * <p>Emits a startup/invocation warning if used outside {@code dev} or {@code test} profiles.
 * Also supports deterministic key health state simulation ({@link #setSimulatedKeyState}) for
 * testing KMS outage and key-revocation workflows.</p>
 */
@Component
public class LocalFileKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFileKeyProvider.class);
    public static final String PROVIDER_ID = "local-file";

    private final KmsContextResolver contextResolver;
    private final Environment environment;
    private final Map<String, KeyHealthStatus> simulatedKeyStates = new ConcurrentHashMap<>();

    @Autowired
    public LocalFileKeyProvider(
            @Autowired(required = false) KmsContextResolver contextResolver,
            @Autowired(required = false) Environment environment) {
        this.contextResolver = contextResolver;
        this.environment = environment;
    }

    public LocalFileKeyProvider() {
        this(null, null);
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    /**
     * Overrides the simulated {@link KeyHealthStatus} for {@code (tenantId, kekKeyId)} in tests.
     */
    public void setSimulatedKeyState(String tenantId, String kekKeyId, KeyHealthStatus status) {
        String key = stateKey(tenantId, kekKeyId);
        if (status == null || status == KeyHealthStatus.HEALTHY) {
            simulatedKeyStates.remove(key);
        } else {
            simulatedKeyStates.put(key, status);
        }
    }

    public void clearSimulatedKeyStates() {
        simulatedKeyStates.clear();
    }

    @Override
    public WrappedKeyResult wrapDek(String tenantId, String kekKeyId, byte[] plaintextDek, byte[] aad) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kekKeyId, "kekKeyId must not be null");
        Objects.requireNonNull(plaintextDek, "plaintextDek must not be null");

        warnIfNotDevOrTest();
        enforceKeyHealthy(tenantId, kekKeyId);

        byte[] kek = resolveLocalKek(tenantId.trim(), kekKeyId.trim());
        try {
            byte[] iv = EnvelopeCipher.generateIv();
            EnvelopeCipher.CiphertextEnvelope env = EnvelopeCipher.encrypt(kek, iv, plaintextDek, aad);
            byte[] packed = PlatformHkdfKeyProvider.packIvAndCiphertext(env.iv(), env.ciphertextWithTag());
            return new WrappedKeyResult(packed, env.iv(), kekKeyId.trim(), "v1");
        } finally {
            Arrays.fill(kek, (byte) 0);
            VarHandle.fullFence();
        }
    }

    @Override
    public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrappedDek, byte[] aad) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(kekKeyId, "kekKeyId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");

        warnIfNotDevOrTest();
        enforceKeyHealthy(tenantId, kekKeyId);

        byte[] kek = resolveLocalKek(tenantId.trim(), kekKeyId.trim());
        try {
            byte[][] unpacked = PlatformHkdfKeyProvider.unpackIvAndCiphertext(wrappedDek);
            byte[] plaintext = EnvelopeCipher.decrypt(kek, unpacked[0], unpacked[1], aad);
            return new DestroyableKeyMaterial(plaintext, true);
        } finally {
            Arrays.fill(kek, (byte) 0);
            VarHandle.fullFence();
        }
    }

    @Override
    public KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId) {
        KeyHealthStatus simulated = simulatedKeyStates.get(stateKey(tenantId, kekKeyId));
        if (simulated != null) {
            return simulated;
        }
        if (kekKeyId != null && kekKeyId.startsWith("file:")) {
            Path path = Path.of(kekKeyId.substring(5).trim());
            if (!Files.isReadable(path)) {
                return KeyHealthStatus.UNREACHABLE;
            }
        }
        return KeyHealthStatus.HEALTHY;
    }

    private void enforceKeyHealthy(String tenantId, String kekKeyId) {
        KeyHealthStatus status = checkKeyHealth(tenantId, kekKeyId);
        if (status == KeyHealthStatus.REVOKED || status == KeyHealthStatus.DISABLED) {
            throw new SpectorMemoryException(ErrorCode.BYOK_KEY_REVOKED, kekKeyId, tenantId);
        }
        if (status == KeyHealthStatus.UNREACHABLE) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    tenantId,
                    kekKeyId,
                    "Local key file is unreachable");
        }
    }

    private byte[] resolveLocalKek(String tenantId, String kekKeyId) {
        try {
            if (kekKeyId.startsWith("file:")) {
                Path file = Path.of(kekKeyId.substring(5).trim());
                byte[] raw = Files.readAllBytes(file);
                String trimmed = new String(raw, StandardCharsets.UTF_8).trim();
                try {
                    byte[] decoded = Base64.getDecoder().decode(trimmed);
                    if (decoded.length == EnvelopeCipher.DEK_LENGTH_BYTES) {
                        return decoded;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Hash arbitrary file content to 32 bytes
                }
                return MessageDigest.getInstance("SHA-256").digest(raw);
            }

            String seedMaterial = "spector-local-kek:" + tenantId + ":" + kekKeyId;
            if (contextResolver != null) {
                KmsConnectionContext ctx = contextResolver.resolve(tenantId, PROVIDER_ID, kekKeyId);
                if (ctx.secret() != null && !ctx.secret().isBlank()) {
                    seedMaterial = seedMaterial + ":" + ctx.secret().trim();
                }
            }
            return MessageDigest.getInstance("SHA-256")
                    .digest(seedMaterial.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    e,
                    tenantId,
                    kekKeyId,
                    "Failed to resolve local KEK: " + e.getMessage());
        }
    }

    private void warnIfNotDevOrTest() {
        if (environment != null && !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            log.warn("[LocalFileKeyProvider] 'local-file' BYOK KeyProvider is active outside dev/test profiles! "
                    + "Use aws-kms, gcp-kms, azure-kv, or hashicorp-vault in production.");
        }
    }

    private static String stateKey(String tenantId, String kekKeyId) {
        return (tenantId != null ? tenantId.trim() : "") + "::" + (kekKeyId != null ? kekKeyId.trim() : "");
    }
}
