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

import com.spectrayan.spector.commons.crypto.DekCache;
import com.spectrayan.spector.commons.crypto.KeyProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of all available {@link KeyProvider} implementations ({@code aws-kms}, {@code gcp-kms},
 * {@code azure-kv}, {@code hashicorp-vault}, {@code local-file}, {@code platform-hkdf}) and the
 * shared {@link DekCache} bean (ADR-0088 §5, Issue #1070).
 */
@Component
public class KeyProviderRegistry {

    private final Map<String, KeyProvider> providersById = new LinkedHashMap<>();
    private final DekCache dekCache;

    @Autowired
    public KeyProviderRegistry(
            List<KeyProvider> providers,
            @Value("${spector.security.byok.dek-cache-ttl-seconds:300}") long dekCacheTtlSeconds) {
        this(providers, new DekCache(Duration.ofSeconds(Math.max(1L, dekCacheTtlSeconds))));
    }

    public KeyProviderRegistry(Collection<KeyProvider> providers, DekCache dekCache) {
        this.dekCache = dekCache != null ? dekCache : new DekCache();
        if (providers != null) {
            for (KeyProvider provider : providers) {
                register(provider);
            }
        }
    }

    public final void register(KeyProvider provider) {
        if (provider == null || provider.providerId() == null) {
            return;
        }
        String canonical = normalizeProviderId(provider.providerId());
        providersById.put(canonical, provider);
    }

    /**
     * Resolves a {@link KeyProvider} by canonical identifier or alias.
     */
    public Optional<KeyProvider> findProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return Optional.empty();
        }
        String canonical = normalizeProviderId(providerId);
        return Optional.ofNullable(providersById.get(canonical));
    }

    /**
     * Resolves a {@link KeyProvider} or throws {@link IllegalArgumentException} if unsupported.
     */
    public KeyProvider getRequiredProvider(String providerId) {
        return findProvider(providerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported BYOK KMS provider '" + providerId + "'. Supported providers: " + supportedProviderIds()));
    }

    /**
     * Returns all canonical registered provider identifiers.
     */
    public Set<String> supportedProviderIds() {
        return Collections.unmodifiableSet(providersById.keySet());
    }

    /**
     * Returns the shared bounded-TTL {@link DekCache}.
     */
    @Bean
    public DekCache dekCache() {
        return dekCache;
    }

    /**
     * Normalizes provider names and common aliases to canonical ADR-0088 provider IDs.
     */
    public static String normalizeProviderId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (norm) {
            case "aws", "aws-kms", "awskms" -> AwsKmsKeyProvider.PROVIDER_ID;
            case "gcp", "gcp-kms", "gcpkms", "google-kms", "cloud-kms" -> GcpKmsKeyProvider.PROVIDER_ID;
            case "azure", "azure-kv", "azure-key-vault", "azurekeyvault" -> AzureKeyVaultKeyProvider.PROVIDER_ID;
            case "vault", "hashicorp-vault", "vault-transit", "hashicorp-vault-transit" -> VaultTransitKeyProvider.PROVIDER_ID;
            case "local", "local-file", "file" -> LocalFileKeyProvider.PROVIDER_ID;
            case "platform", "platform-hkdf", "default" -> PlatformHkdfKeyProvider.PROVIDER_ID;
            default -> norm;
        };
    }
}
