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
package com.spectrayan.spector.synapse.architecture;

import com.spectrayan.spector.cluster.routing.ClusterValidation;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates that {@link ClusterValidation} in {@code spector-cluster} and {@link StoragePaths} in
 * {@code spector-kernel} enforce an identical fail-closed validation contract for identifiers (Req R1.4, R2.4).
 */
@DisplayName("Task 1.4: ClusterValidation and StoragePaths Contract Agreement Test")
class ValidationAgreementTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "valid-namespace-1",
            "018f9b8c000070008000000000000001",
            "user_workspace_99",
            "a"
    })
    @DisplayName("Valid namespace identifiers pass on both validators")
    void validNamespacesPassBoth(String validId) {
        assertThatCode(() -> ClusterValidation.validateNamespaceId(validId)).doesNotThrowAnyException();
        assertThatCode(() -> StoragePaths.validateNamespaceId(validId)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "ns/with/slash",
            "ns\\with\\backslash",
            "ns.with.dot",
            "ns\u0000nullbyte",
            "ns\nnewline"
    })
    @DisplayName("Invalid namespace identifiers fail on both validators")
    void invalidNamespacesFailBoth(String invalidId) {
        assertThatThrownBy(() -> ClusterValidation.validateNamespaceId(invalidId))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> StoragePaths.validateNamespaceId(invalidId))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Null namespace fails on both validators")
    void nullNamespaceFailsBoth() {
        assertThatThrownBy(() -> ClusterValidation.validateNamespaceId(null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> StoragePaths.validateNamespaceId(null))
                .isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tenant-alpha",
            "018f9b8c000070008000000000000001",
            "acme_corp"
    })
    @DisplayName("Valid lowercase tenant identifiers pass on both validators")
    void validTenantsPassBoth(String validTenant) {
        assertThatCode(() -> ClusterValidation.validateTenantId(validTenant)).doesNotThrowAnyException();
        assertThatCode(() -> StoragePaths.validateTenantId(validTenant)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Tenant-Uppercase",
            "ACME",
            "tenant/slash",
            "tenant.dot"
    })
    @DisplayName("Invalid or uppercase tenant identifiers fail on both validators")
    void invalidTenantsFailBoth(String invalidTenant) {
        assertThatThrownBy(() -> ClusterValidation.validateTenantId(invalidTenant))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> StoragePaths.validateTenantId(invalidTenant))
                .isInstanceOf(RuntimeException.class);
    }
}
