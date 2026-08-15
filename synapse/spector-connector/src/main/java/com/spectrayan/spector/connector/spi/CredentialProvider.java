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

import java.util.Optional;

/**
 * SPI for resolving credentials (API keys, tokens, secrets, connection strings)
 * at route instantiation or outbound dispatch time.
 *
 * <p>Implementations can resolve secrets from encrypted relational vaults,
 * external KMS systems (HashiCorp Vault, AWS Secrets Manager), or environment variables.</p>
 */
public interface CredentialProvider {

    /**
     * Resolves a credential by its reference key within a tenant context.
     *
     * @param credentialRef the reference (e.g. "tenant:whatsapp-token", "env:SLACK_BOT_TOKEN")
     * @param tenantId      the tenant identifier context (optional, defaults to "default" if null)
     * @return the resolved secret, or empty if not found
     */
    default Optional<String> resolve(String credentialRef, String tenantId) {
        return resolve(credentialRef);
    }

    /**
     * Resolves a credential by its reference key using default tenant context.
     *
     * @param credentialRef the reference (e.g. "env:OPENAI_API_KEY", "slack-token")
     * @return the resolved secret, or empty if not found
     */
    Optional<String> resolve(String credentialRef);

    /**
     * Default implementation that resolves from environment variables.
     *
     * <p>Supports both direct env var names (e.g., {@code OPENAI_API_KEY})
     * and {@code env:} prefixed references (e.g., {@code env:OPENAI_API_KEY}).</p>
     */
    static CredentialProvider fromEnvironment() {
        return new CredentialProvider() {
            @Override
            public Optional<String> resolve(String credentialRef, String tenantId) {
                return resolve(credentialRef);
            }

            @Override
            public Optional<String> resolve(String credentialRef) {
                if (credentialRef == null || credentialRef.isBlank()) {
                    return Optional.empty();
                }
                String key = credentialRef.startsWith("env:") ? credentialRef.substring(4) : credentialRef;
                return Optional.ofNullable(System.getenv(key))
                        .or(() -> Optional.ofNullable(System.getProperty(key)));
            }
        };
    }
}
