/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
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
