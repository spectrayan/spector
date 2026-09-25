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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PassthroughCatalog Security Hardening Tests")
class PassthroughCatalogTest {

    private final PassthroughCatalog catalog = new PassthroughCatalog();

    @Test
    @DisplayName("resolve and authorize work for backward compatibility in test mode")
    void resolveAndAuthorizeSucceed() {
        Optional<NamespaceRecord> record = catalog.resolve("test-acc", "default");
        assertThat(record).isPresent();
        assertThat(record.get().ownerAccountId()).isEqualTo("test-acc");

        var grant = catalog.authorize("test-acc", "default", GrantRole.WRITER);
        assertThat(grant).isPresent();
        assertThat(grant.get().role()).isEqualTo(GrantRole.OWNER);
    }

    @Test
    @DisplayName("tombstone throws UnsupportedOperationException to prevent accidental test-stub deletion")
    void tombstoneThrows() {
        assertThatThrownBy(() -> catalog.tombstone("test-acc", "default"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support namespace tombstone");
    }

    @Test
    @DisplayName("setLegalHold throws UnsupportedOperationException to prevent fake legal hold claims")
    void setLegalHoldThrows() {
        assertThatThrownBy(() -> catalog.setLegalHold("test-acc", "default", true))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support legal hold mutations");
    }

    @Test
    @DisplayName("revokeNamespaceGrant throws UnsupportedOperationException")
    void revokeNamespaceGrantThrows() {
        assertThatThrownBy(() -> catalog.revokeNamespaceGrant("test-acc", "default", "g-1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support grant revocation");
    }

    @Test
    @DisplayName("revokeGrant throws UnsupportedOperationException")
    void revokeGrantThrows() {
        assertThatThrownBy(() -> catalog.revokeGrant("g-1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support grant revocation");
    }

    @Test
    @DisplayName("resetNamespace throws UnsupportedOperationException")
    void resetNamespaceThrows() {
        assertThatThrownBy(() -> catalog.resetNamespace("test-acc", "default"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support namespace reset");
    }
}
