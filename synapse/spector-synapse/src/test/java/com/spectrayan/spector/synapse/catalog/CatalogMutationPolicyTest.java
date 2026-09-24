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
package com.spectrayan.spector.synapse.catalog;

import com.spectrayan.spector.memory.policy.DeletionKind;
import com.spectrayan.spector.memory.policy.DeletionRequest;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceLegalHoldException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("CatalogMutationPolicy — Engine Deletion Policy & Legal Hold Enforcement")
class CatalogMutationPolicyTest {

    private AccountCatalog catalog;
    private static final String ACCOUNT_ID = "acc-corp";
    private static final String NAMESPACE_ID = "ns-finance";

    private CatalogMutationPolicy policy;

    @BeforeEach
    void setUp() {
        catalog = mock(AccountCatalog.class);
        policy = new CatalogMutationPolicy(catalog, ACCOUNT_ID);
    }

    private NamespaceRecord makeRecord(boolean legalHold) {
        return new NamespaceRecord(
                NAMESPACE_ID,
                "finance-slug",
                ACCOUNT_ID,
                NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE,
                "Finance Department",
                "Financial records",
                null,
                Instant.now(),
                Instant.now(),
                legalHold
        );
    }

    @Test
    @DisplayName("FORGET is allowed even when namespace is under active legal hold")
    void forgetAllowedUnderLegalHold() {
        // FORGET only tombstones; bytes remain intact on disk for legal discovery
        var request = DeletionRequest.forget(NAMESPACE_ID, "mem-1");

        assertThatCode(() -> policy.checkDeletion(request))
                .doesNotThrowAnyException();

        // Catalog should not even need to be consulted for non-destructive operations
        verifyNoInteractions(catalog);
    }

    @Test
    @DisplayName("PURGE is refused with NamespaceLegalHoldException when namespace is under legal hold")
    void purgeRefusedUnderLegalHold() {
        when(catalog.resolve(ACCOUNT_ID, NAMESPACE_ID)).thenReturn(Optional.of(makeRecord(true)));

        var request = DeletionRequest.purge(NAMESPACE_ID, "mem-1");

        assertThatThrownBy(() -> policy.checkDeletion(request))
                .isInstanceOf(NamespaceLegalHoldException.class)
                .hasMessageContaining(NAMESPACE_ID);
    }

    @Test
    @DisplayName("VACUUM and ERASE_NAMESPACE are also refused under active legal hold")
    void destructiveKindsRefusedUnderLegalHold() {
        when(catalog.resolve(ACCOUNT_ID, NAMESPACE_ID)).thenReturn(Optional.of(makeRecord(true)));

        var vacuumReq = DeletionRequest.vacuum(NAMESPACE_ID);
        assertThatThrownBy(() -> policy.checkDeletion(vacuumReq))
                .isInstanceOf(NamespaceLegalHoldException.class);

        var eraseReq = DeletionRequest.eraseNamespace(NAMESPACE_ID);
        assertThatThrownBy(() -> policy.checkDeletion(eraseReq))
                .isInstanceOf(NamespaceLegalHoldException.class);
    }

    @Test
    @DisplayName("PURGE is permitted when namespace is NOT under legal hold")
    void purgeAllowedWhenNotUnderLegalHold() {
        when(catalog.resolve(ACCOUNT_ID, NAMESPACE_ID)).thenReturn(Optional.of(makeRecord(false)));

        var request = DeletionRequest.purge(NAMESPACE_ID, "mem-1");

        assertThatCode(() -> policy.checkDeletion(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PURGE fails closed (IllegalStateException) when catalog lookup fails")
    void purgeFailsClosedOnCatalogFailure() {
        when(catalog.resolve(ACCOUNT_ID, NAMESPACE_ID))
                .thenThrow(new RuntimeException("Database connection timeout"));

        var request = DeletionRequest.purge(NAMESPACE_ID, "mem-1");

        assertThatThrownBy(() -> policy.checkDeletion(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot verify legal hold");
    }

    @Test
    @DisplayName("PURGE fails closed (IllegalStateException) when namespace is unknown/unresolvable")
    void purgeFailsClosedOnUnknownNamespace() {
        when(catalog.resolve(ACCOUNT_ID, NAMESPACE_ID)).thenReturn(Optional.empty());

        var request = DeletionRequest.purge(NAMESPACE_ID, "mem-1");

        assertThatThrownBy(() -> policy.checkDeletion(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unresolvable namespace");
    }
}
