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
package com.spectrayan.spector.connector.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Composite {@link CredentialProvider} that chains multiple providers in priority order.
 *
 * <p>Standard resolution order:
 * <ol>
 *   <li>Database Encrypted Credential Vault (Tenant / User BYOK)</li>
 *   <li>Enterprise External KMS / Vault (HashiCorp, AWS, Azure)</li>
 *   <li>Environment Variables & System Properties fallback</li>
 * </ol>
 * </p>
 */
public class CompositeCredentialProvider implements CredentialProvider {

    private final List<CredentialProvider> providers;

    public CompositeCredentialProvider(List<CredentialProvider> providers) {
        this.providers = providers != null
                ? new ArrayList<>(providers)
                : new ArrayList<>();
    }

    public static CompositeCredentialProvider of(CredentialProvider... providers) {
        List<CredentialProvider> list = new ArrayList<>();
        if (providers != null) {
            for (CredentialProvider p : providers) {
                if (p != null) list.add(p);
            }
        }
        return new CompositeCredentialProvider(list);
    }

    public void addProvider(CredentialProvider provider) {
        if (provider != null) {
            providers.add(provider);
        }
    }

    public List<CredentialProvider> providers() {
        return Collections.unmodifiableList(providers);
    }

    @Override
    public Optional<String> resolve(String credentialRef, String tenantId) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Optional.empty();
        }

        String effectiveTenant = tenantId != null ? tenantId : "default";

        // Direct env: prefix bypasses DB/vault and checks environment
        if (credentialRef.startsWith("env:")) {
            String envKey = credentialRef.substring(4);
            return Optional.ofNullable(System.getenv(envKey))
                    .or(() -> Optional.ofNullable(System.getProperty(envKey)));
        }

        for (CredentialProvider provider : providers) {
            try {
                Optional<String> secret = provider.resolve(credentialRef, effectiveTenant);
                if (secret.isPresent() && !secret.get().isBlank()) {
                    return secret;
                }
            } catch (Exception ignored) {
                // Continue to next provider in fallback chain
            }
        }

        // Fallback to environment variable if exact name matches
        return Optional.ofNullable(System.getenv(credentialRef))
                .or(() -> Optional.ofNullable(System.getProperty(credentialRef)));
    }

    @Override
    public Optional<String> resolve(String credentialRef) {
        return resolve(credentialRef, "default");
    }
}
