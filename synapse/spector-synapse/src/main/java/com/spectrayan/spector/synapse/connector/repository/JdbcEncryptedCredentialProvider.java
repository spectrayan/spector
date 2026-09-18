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
package com.spectrayan.spector.synapse.connector.repository;

import com.spectrayan.spector.connector.spi.CredentialProvider;
import com.spectrayan.spector.synapse.connector.service.CredentialService;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Adapter implementing the {@link CredentialProvider} SPI for the connector engine
 * by delegating to the domain {@link CredentialService}.
 */
@Component
public class JdbcEncryptedCredentialProvider implements CredentialProvider {

    private final CredentialService credentialService;

    public JdbcEncryptedCredentialProvider(CredentialService credentialService) {
        this.credentialService = Objects.requireNonNull(credentialService, "CredentialService must not be null");
    }

    @Override
    public Optional<String> resolve(String credentialRef, String tenantId) {
        return credentialService.resolveSecret(credentialRef, tenantId);
    }

    @Override
    public Optional<String> resolve(String credentialRef) {
        return credentialService.resolveSecret(credentialRef, "default");
    }
}
