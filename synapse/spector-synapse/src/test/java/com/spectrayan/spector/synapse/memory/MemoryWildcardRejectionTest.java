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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryWildcardRejection — Unit Tests")
class MemoryWildcardRejectionTest {

    @Mock
    private AccountCatalog catalog;

    @Mock
    private MemoryRegistry memoryRegistry;

    private MemoryRequestBinder binder;

    @BeforeEach
    void setUp() {
        SynapseProperties props = new SynapseProperties();
        props.auth().setEnabled(true);
        binder = new MemoryRequestBinder(catalog, memoryRegistry, props, null, null);
    }

    @Test
    @DisplayName("Rejects wildcard '*' on single-namespace binding")
    void testRejectsWildcard() {
        Authentication auth = new TestingAuthenticationToken("01JXYZTEST001", "password");

        assertThatThrownBy(() -> binder.bind(auth, Optional.of("*")))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("Wildcard '*' or multi-namespace selection is not permitted");
    }

    @Test
    @DisplayName("Rejects comma-separated multi-namespace selectors on single-namespace binding")
    void testRejectsCommaSeparatedSelectors() {
        Authentication auth = new TestingAuthenticationToken("01JXYZTEST001", "password");

        assertThatThrownBy(() -> binder.bind(auth, Optional.of("default,project-alpha")))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("Wildcard '*' or multi-namespace selection is not permitted");
    }
}
