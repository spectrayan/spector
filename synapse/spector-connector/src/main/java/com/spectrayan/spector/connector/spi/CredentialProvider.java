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
 * SPI for resolving credentials (API keys, tokens, secrets) at route
 * instantiation time.
 *
 * <p>Implementations can resolve secrets from environment variables,
 * file-based vaults, or external secret managers. The default
 * implementation resolves from environment variables.</p>
 */
public interface CredentialProvider {

    /**
     * Resolves a credential by its reference key.
     *
     * @param credentialRef the reference (e.g., env var name, vault path)
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
        return ref -> {
            String key = ref.startsWith("env:") ? ref.substring(4) : ref;
            return Optional.ofNullable(System.getenv(key));
        };
    }
}
