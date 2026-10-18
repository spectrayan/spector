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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.EnvelopeCipher;
import com.spectrayan.spector.commons.crypto.KeyHealthStatus;
import com.spectrayan.spector.commons.crypto.WrappedKeyResult;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.synapse.security.crypto.AesGcmCipher;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Protocol and wire-level integration tests for all six {@link com.spectrayan.spector.commons.crypto.KeyProvider}
 * implementations ({@link AwsKmsKeyProvider}, {@link VaultTransitKeyProvider}, {@link GcpKmsKeyProvider},
 * {@link AzureKeyVaultKeyProvider}, {@link LocalFileKeyProvider}, {@link PlatformHkdfKeyProvider})
 * (ADR-0088 §5, Issue #1070).
 */
@DisplayName("KMS KeyProvider Adapters Wire & Protocol Tests (Issue #1070)")
class KmsProvidersWireTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private byte[] serverMasterKek;
    private final AtomicBoolean keyRevoked = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws Exception {
        keyRevoked.set(false);
        serverMasterKek = MessageDigest.getInstance("SHA-256")
                .digest("kms-wire-server-master-kek-2026".getBytes(StandardCharsets.UTF_8));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleKmsRequest);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("AwsKmsKeyProvider signs requests with SigV4, binds EncryptionContext, and handles revoked keys")
    void awsKmsWrapUnwrapAndRevocation() {
        KmsContextResolver resolver = new KmsContextResolver(null, null);
        AwsKmsKeyProvider provider = new AwsKmsKeyProvider(
                resolver, mapper, HttpClient.newHttpClient(), Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), java.time.ZoneOffset.UTC));

        KmsConnectionContext ctx = new KmsConnectionContext(
                "acme-corp",
                "aws-kms",
                "arn:aws:kms:us-east-1:123456789012:key/mrk-1234",
                "aws-cred",
                "AKIAIOSFODNN7EXAMPLE:wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                "us-east-1",
                baseUrl,
                Map.of());

        resolver.withCandidateContext(ctx, () -> {
            assertThat(provider.checkKeyHealth("acme-corp", ctx.kekKeyId())).isEqualTo(KeyHealthStatus.HEALTHY);

            try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
                byte[] aad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-1", 1);
                WrappedKeyResult wrapped = provider.wrapDek("acme-corp", ctx.kekKeyId(), dek.bytes(), aad);
                assertThat(wrapped.wrappedDek()).isNotEmpty();

                try (DestroyableKeyMaterial unwrapped = provider.unwrapDek(
                        "acme-corp", ctx.kekKeyId(), wrapped.wrappedDek(), aad)) {
                    assertThat(unwrapped.bytes()).isEqualTo(dek.bytes());
                }

                // Mismatched AAD must fail with SPE-310-026 (MEMORY_AUTH_TAG_MISMATCH)
                byte[] wrongAad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-other", 1);
                assertThatThrownBy(() -> provider.unwrapDek("acme-corp", ctx.kekKeyId(), wrapped.wrappedDek(), wrongAad))
                        .isInstanceOf(SpectorMemoryException.class)
                        .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                                .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));

                // Revoke key in KMS -> checkKeyHealth returns REVOKED and unwrap throws SPE-820-010 (BYOK_KEY_REVOKED)
                keyRevoked.set(true);
                assertThat(provider.checkKeyHealth("acme-corp", ctx.kekKeyId())).isEqualTo(KeyHealthStatus.REVOKED);
                assertThatThrownBy(() -> provider.unwrapDek("acme-corp", ctx.kekKeyId(), wrapped.wrappedDek(), aad))
                        .isInstanceOf(SpectorMemoryException.class)
                        .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                                .isEqualTo(ErrorCode.BYOK_KEY_REVOKED));
            }
            return null;
        });
    }

    @Test
    @DisplayName("VaultTransitKeyProvider wraps/unwraps with X-Vault-Token and base64 context AAD")
    void vaultTransitWrapUnwrapAndRevocation() {
        KmsContextResolver resolver = new KmsContextResolver(null, null);
        VaultTransitKeyProvider provider = new VaultTransitKeyProvider(resolver, mapper, HttpClient.newHttpClient());

        KmsConnectionContext ctx = new KmsConnectionContext(
                "acme-corp",
                "hashicorp-vault",
                "transit/keys/spector-acme",
                "vault-cred",
                "hvs.CAESIJ1234567890",
                null,
                baseUrl,
                Map.of("vaultNamespace", "enterprise/acme"));

        resolver.withCandidateContext(ctx, () -> {
            assertThat(provider.checkKeyHealth("acme-corp", "spector-acme")).isEqualTo(KeyHealthStatus.HEALTHY);

            try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
                byte[] aad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-vault", 1);
                WrappedKeyResult wrapped = provider.wrapDek("acme-corp", "spector-acme", dek.bytes(), aad);
                assertThat(new String(wrapped.wrappedDek(), StandardCharsets.UTF_8)).startsWith("vault:v2:");
                assertThat(wrapped.kekVersion()).isEqualTo("v2");

                try (DestroyableKeyMaterial unwrapped = provider.unwrapDek(
                        "acme-corp", "spector-acme", wrapped.wrappedDek(), aad)) {
                    assertThat(unwrapped.bytes()).isEqualTo(dek.bytes());
                }

                // Wrong AAD context -> SPE-310-026
                byte[] wrongAad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-tampered", 1);
                assertThatThrownBy(() -> provider.unwrapDek("acme-corp", "spector-acme", wrapped.wrappedDek(), wrongAad))
                        .isInstanceOf(SpectorMemoryException.class)
                        .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                                .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));

                // Revoke key in Vault -> SPE-820-010
                keyRevoked.set(true);
                assertThat(provider.checkKeyHealth("acme-corp", "spector-acme")).isEqualTo(KeyHealthStatus.REVOKED);
                assertThatThrownBy(() -> provider.unwrapDek("acme-corp", "spector-acme", wrapped.wrappedDek(), aad))
                        .isInstanceOf(SpectorMemoryException.class)
                        .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                                .isEqualTo(ErrorCode.BYOK_KEY_REVOKED));
            }
            return null;
        });
    }

    @Test
    @DisplayName("GcpKmsKeyProvider wraps/unwraps with additionalAuthenticatedData and extracts cryptoKeyVersion")
    void gcpKmsWrapUnwrapAndRevocation() {
        KmsContextResolver resolver = new KmsContextResolver(null, null);
        GcpKmsKeyProvider provider = new GcpKmsKeyProvider(resolver, mapper, HttpClient.newHttpClient());

        String resourceName = "projects/acme-gcp/locations/us-central1/keyRings/spector/cryptoKeys/tenant-kek";
        KmsConnectionContext ctx = new KmsConnectionContext(
                "acme-corp",
                "gcp-kms",
                resourceName,
                "gcp-cred",
                "ya29.gcp-oauth-access-token",
                "us-central1",
                baseUrl,
                Map.of());

        resolver.withCandidateContext(ctx, () -> {
            assertThat(provider.checkKeyHealth("acme-corp", resourceName)).isEqualTo(KeyHealthStatus.HEALTHY);

            try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
                byte[] aad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-gcp", 1);
                WrappedKeyResult wrapped = provider.wrapDek("acme-corp", resourceName, dek.bytes(), aad);
                assertThat(wrapped.kekVersion()).isEqualTo("v3");

                try (DestroyableKeyMaterial unwrapped = provider.unwrapDek(
                        "acme-corp", resourceName, wrapped.wrappedDek(), aad)) {
                    assertThat(unwrapped.bytes()).isEqualTo(dek.bytes());
                }

                // Wrong AAD -> SPE-310-026
                byte[] wrongAad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-wrong", 1);
                assertThatThrownBy(() -> provider.unwrapDek("acme-corp", resourceName, wrapped.wrappedDek(), wrongAad))
                        .isInstanceOf(SpectorMemoryException.class)
                        .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                                .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));

                // Revoke key -> SPE-820-010
                keyRevoked.set(true);
                assertThat(provider.checkKeyHealth("acme-corp", resourceName)).isEqualTo(KeyHealthStatus.REVOKED);
                assertThatThrownBy(() -> provider.unwrapDek("acme-corp", resourceName, wrapped.wrappedDek(), aad))
                        .isInstanceOf(SpectorMemoryException.class)
                        .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                                .isEqualTo(ErrorCode.BYOK_KEY_REVOKED));
            }
            return null;
        });
    }

    @Test
    @DisplayName("AzureKeyVaultKeyProvider wraps/unwraps with RSA-OAEP-256 and verifies SHA-256 AAD digest")
    void azureKeyVaultWrapUnwrapAndAadVerification() {
        KmsContextResolver resolver = new KmsContextResolver(null, null);
        AzureKeyVaultKeyProvider provider = new AzureKeyVaultKeyProvider(resolver, mapper, HttpClient.newHttpClient());

        String keyId = "keys/acme-kek/9f8e7d6c";
        KmsConnectionContext ctx = new KmsConnectionContext(
                "acme-corp",
                "azure-kv",
                keyId,
                "azure-cred",
                "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.azure-token",
                "eastus",
                baseUrl,
                Map.of());

        resolver.withCandidateContext(ctx, () -> {
            assertThat(provider.checkKeyHealth("acme-corp", keyId)).isEqualTo(KeyHealthStatus.HEALTHY);

            try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
                byte[] aad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-azure", 1);
                WrappedKeyResult wrapped = provider.wrapDek("acme-corp", keyId, dek.bytes(), aad);
                assertThat(wrapped.kekVersion()).isEqualTo("9f8e7d6c");

                try (DestroyableKeyMaterial unwrapped = provider.unwrapDek(
                        "acme-corp", keyId, wrapped.wrappedDek(), aad)) {
                    assertThat(unwrapped.bytes()).isEqualTo(dek.bytes());
                }

                // Cross-tenant or AAD tampering must fail SHA-256 AAD digest verification with SPE-310-026
                byte[] wrongAad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-tampered", 1);
                assertThatThrownBy(() -> provider.unwrapDek("acme-corp", keyId, wrapped.wrappedDek(), wrongAad))
                        .isInstanceOf(SpectorMemoryException.class)
                        .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                                .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));
            }
            return null;
        });
    }

    @Test
    @DisplayName("LocalFileKeyProvider and PlatformHkdfKeyProvider wrap/unwrap DEKs and isolate tenants")
    void localFileAndPlatformHkdfProviders(@TempDir Path tempDir) throws Exception {
        Path keyFile = tempDir.resolve("tenant-kek.key");
        byte[] fileKeyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            fileKeyBytes[i] = (byte) (i + 33);
        }
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(fileKeyBytes));

        LocalFileKeyProvider localProvider = new LocalFileKeyProvider();
        PlatformHkdfKeyProvider platformProvider = new PlatformHkdfKeyProvider(new AesGcmCipher("test-master-secret-key-32-bytes!"));

        String localKeyId = "file:" + keyFile.toAbsolutePath();
        byte[] aad = EnvelopeCipher.buildNamespaceDekAad("acme-corp", "ns-local", 1);

        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
            WrappedKeyResult localWrapped = localProvider.wrapDek("acme-corp", localKeyId, dek.bytes(), aad);
            try (DestroyableKeyMaterial localUnwrapped = localProvider.unwrapDek(
                    "acme-corp", localKeyId, localWrapped.wrappedDek(), aad)) {
                assertThat(localUnwrapped.bytes()).isEqualTo(dek.bytes());
            }

            WrappedKeyResult platformWrapped = platformProvider.wrapDek("acme-corp", "platform-v1", dek.bytes(), aad);
            try (DestroyableKeyMaterial platformUnwrapped = platformProvider.unwrapDek(
                    "acme-corp", "platform-v1", platformWrapped.wrappedDek(), aad)) {
                assertThat(platformUnwrapped.bytes()).isEqualTo(dek.bytes());
            }

            // Cross-tenant unwrap on PlatformHkdfKeyProvider must fail with SPE-310-026
            assertThatThrownBy(() -> platformProvider.unwrapDek(
                    "other-tenant", "platform-v1", platformWrapped.wrappedDek(), aad))
                    .isInstanceOf(SpectorMemoryException.class)
                    .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));
        }
    }

    // ── Embedded Multi-Cloud KMS Wire Server ──

    private void handleKmsRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String amzTarget = exchange.getRequestHeaders().getFirst("X-Amz-Target");
        byte[] reqBytes = exchange.getRequestBody().readAllBytes();

        try {
            if (amzTarget != null) {
                handleAwsKms(exchange, amzTarget, reqBytes);
            } else if (path.startsWith("/v1/transit/")) {
                handleVaultTransit(exchange, method, path, reqBytes);
            } else if (path.startsWith("/v1/projects/")) {
                handleGcpKms(exchange, method, path, reqBytes);
            } else if (path.startsWith("/keys/")) {
                handleAzureKeyVault(exchange, method, path, reqBytes);
            } else {
                sendJson(exchange, 404, Map.of("error", "Unknown path: " + path));
            }
        } catch (SpectorMemoryException sme) {
            if (sme.errorCode() == ErrorCode.MEMORY_AUTH_TAG_MISMATCH) {
                sendJson(exchange, 400, Map.of(
                        "__type", "InvalidCiphertextException",
                        "message", "Ciphertext or AAD authentication failed: unable to decrypt",
                        "error", "decryption failed: additional authenticated data mismatch"));
            } else {
                sendJson(exchange, 500, Map.of("error", sme.getMessage()));
            }
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleAwsKms(HttpExchange exchange, String amzTarget, byte[] reqBytes) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("AWS4-HMAC-SHA256 Credential=")) {
            sendJson(exchange, 403, Map.of("__type", "AccessDeniedException", "message", "Missing SigV4 header"));
            return;
        }
        if (keyRevoked.get()) {
            if ("TrentService.DescribeKey".equals(amzTarget)) {
                sendJson(exchange, 200, Map.of("KeyMetadata", Map.of("KeyState", "PendingDeletion", "Enabled", false)));
                return;
            }
            sendJson(exchange, 400, Map.of("__type", "DisabledException", "message", "Key is disabled or pending deletion"));
            return;
        }

        JsonNode req = mapper.readTree(reqBytes);
        if ("TrentService.DescribeKey".equals(amzTarget)) {
            sendJson(exchange, 200, Map.of("KeyMetadata", Map.of("KeyId", req.path("KeyId").asText(), "KeyState", "Enabled", "Enabled", true)));
            return;
        }

        byte[] contextBytes = mapper.writeValueAsBytes(req.path("EncryptionContext"));
        if ("TrentService.Encrypt".equals(amzTarget)) {
            byte[] plaintext = Base64.getDecoder().decode(req.path("Plaintext").asText());
            byte[] iv = EnvelopeCipher.generateIv();
            EnvelopeCipher.CiphertextEnvelope env = EnvelopeCipher.encrypt(serverMasterKek, iv, plaintext, contextBytes);
            byte[] blob = ByteBuffer.allocate(iv.length + env.ciphertextWithTag().length)
                    .put(iv).put(env.ciphertextWithTag()).array();
            sendJson(exchange, 200, Map.of(
                    "KeyId", req.path("KeyId").asText(),
                    "CiphertextBlob", Base64.getEncoder().encodeToString(blob)));
        } else if ("TrentService.Decrypt".equals(amzTarget)) {
            byte[] blob = Base64.getDecoder().decode(req.path("CiphertextBlob").asText());
            byte[][] unpacked = PlatformHkdfKeyProvider.unpackIvAndCiphertext(blob);
            byte[] decrypted = EnvelopeCipher.decrypt(serverMasterKek, unpacked[0], unpacked[1], contextBytes);
            sendJson(exchange, 200, Map.of(
                    "KeyId", req.path("KeyId").asText(),
                    "Plaintext", Base64.getEncoder().encodeToString(decrypted)));
        }
    }

    private void handleVaultTransit(HttpExchange exchange, String method, String path, byte[] reqBytes) throws IOException {
        String token = exchange.getRequestHeaders().getFirst("X-Vault-Token");
        if (token == null || token.isBlank() || keyRevoked.get()) {
            sendJson(exchange, 403, Map.of("errors", java.util.List.of("permission denied: key disabled or revoked")));
            return;
        }
        if ("GET".equalsIgnoreCase(method) && path.contains("/keys/")) {
            sendJson(exchange, 200, Map.of("data", Map.of("supports_encryption", true, "supports_decryption", true)));
            return;
        }

        JsonNode req = mapper.readTree(reqBytes);
        byte[] aad = req.has("context") ? Base64.getDecoder().decode(req.path("context").asText()) : new byte[0];
        if (path.contains("/encrypt/")) {
            byte[] plaintext = Base64.getDecoder().decode(req.path("plaintext").asText());
            byte[] iv = EnvelopeCipher.generateIv();
            EnvelopeCipher.CiphertextEnvelope env = EnvelopeCipher.encrypt(serverMasterKek, iv, plaintext, aad);
            byte[] packed = PlatformHkdfKeyProvider.packIvAndCiphertext(iv, env.ciphertextWithTag());
            String vaultCiphertext = "vault:v2:" + Base64.getEncoder().encodeToString(packed);
            sendJson(exchange, 200, Map.of("data", Map.of("ciphertext", vaultCiphertext, "key_version", 2)));
        } else if (path.contains("/decrypt/")) {
            String vaultCiphertext = req.path("ciphertext").asText();
            String b64 = vaultCiphertext.substring("vault:v2:".length());
            byte[][] unpacked = PlatformHkdfKeyProvider.unpackIvAndCiphertext(Base64.getDecoder().decode(b64));
            byte[] decrypted = EnvelopeCipher.decrypt(serverMasterKek, unpacked[0], unpacked[1], aad);
            sendJson(exchange, 200, Map.of("data", Map.of("plaintext", Base64.getEncoder().encodeToString(decrypted))));
        }
    }

    private void handleGcpKms(HttpExchange exchange, String method, String path, byte[] reqBytes) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            sendJson(exchange, 403, Map.of("error", Map.of("status", "PERMISSION_DENIED")));
            return;
        }
        if ("GET".equalsIgnoreCase(method)) {
            String state = keyRevoked.get() ? "DESTROYED" : "ENABLED";
            sendJson(exchange, 200, Map.of("primary", Map.of("state", state)));
            return;
        }
        if (keyRevoked.get()) {
            sendJson(exchange, 400, Map.of("error", Map.of("status", "FAILED_PRECONDITION", "message", "Key is DESTROYED")));
            return;
        }

        JsonNode req = mapper.readTree(reqBytes);
        byte[] aad = req.has("additionalAuthenticatedData")
                ? Base64.getDecoder().decode(req.path("additionalAuthenticatedData").asText())
                : new byte[0];
        if (path.endsWith(":encrypt")) {
            byte[] plaintext = Base64.getDecoder().decode(req.path("plaintext").asText());
            byte[] iv = EnvelopeCipher.generateIv();
            EnvelopeCipher.CiphertextEnvelope env = EnvelopeCipher.encrypt(serverMasterKek, iv, plaintext, aad);
            byte[] packed = PlatformHkdfKeyProvider.packIvAndCiphertext(iv, env.ciphertextWithTag());
            sendJson(exchange, 200, Map.of(
                    "name", path.substring(4, path.length() - ":encrypt".length()) + "/cryptoKeyVersions/3",
                    "ciphertext", Base64.getEncoder().encodeToString(packed)));
        } else if (path.endsWith(":decrypt")) {
            byte[] packed = Base64.getDecoder().decode(req.path("ciphertext").asText());
            byte[][] unpacked = PlatformHkdfKeyProvider.unpackIvAndCiphertext(packed);
            byte[] decrypted = EnvelopeCipher.decrypt(serverMasterKek, unpacked[0], unpacked[1], aad);
            sendJson(exchange, 200, Map.of("plaintext", Base64.getEncoder().encodeToString(decrypted)));
        }
    }

    private void handleAzureKeyVault(HttpExchange exchange, String method, String path, byte[] reqBytes) throws IOException {
        if (keyRevoked.get()) {
            sendJson(exchange, 403, Map.of("error", Map.of("code", "KeyDisabled")));
            return;
        }
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, Map.of("attributes", Map.of("enabled", true)));
            return;
        }

        JsonNode req = mapper.readTree(reqBytes);
        byte[] value = Base64.getUrlDecoder().decode(req.path("value").asText());
        if (path.endsWith("/wrapkey")) {
            byte[] iv = EnvelopeCipher.generateIv();
            EnvelopeCipher.CiphertextEnvelope env = EnvelopeCipher.encrypt(serverMasterKek, iv, value, null);
            byte[] packed = PlatformHkdfKeyProvider.packIvAndCiphertext(iv, env.ciphertextWithTag());
            sendJson(exchange, 200, Map.of(
                    "kid", baseUrl + "/keys/acme-kek/9f8e7d6c",
                    "value", Base64.getUrlEncoder().withoutPadding().encodeToString(packed)));
        } else if (path.endsWith("/unwrapkey")) {
            byte[][] unpacked = PlatformHkdfKeyProvider.unpackIvAndCiphertext(value);
            byte[] decrypted = EnvelopeCipher.decrypt(serverMasterKek, unpacked[0], unpacked[1], null);
            sendJson(exchange, 200, Map.of(
                    "kid", baseUrl + "/keys/acme-kek/9f8e7d6c",
                    "value", Base64.getUrlEncoder().withoutPadding().encodeToString(decrypted)));
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
