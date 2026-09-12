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
package com.spectrayan.spector.cluster.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RoutingKey} format, Redis Cluster hash-tag construction, and validation
 * (ADR-0034 §15.2, Req R2.1–R2.5, Decision B2).
 */
class RoutingKeyFormatTest {

    @Test
    @DisplayName("Req R2.2: Canonical hash-tag format colocation on same cluster slot")
    void canonicalRedisHashKeyFormat() {
        RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-alpha", "ns-user-123");

        assertThat(key.redisHashKey())
                .isEqualTo("rt:ns:{cell-1:tenant-alpha:ns-user-123}");

        assertThat(key.redisHintKey())
                .isEqualTo("rt:ns:{cell-1:tenant-alpha:ns-user-123}:hint");

        // Assert literal hash tags match exactly so Redis Cluster maps both keys to the same slot
        String hashTag = key.redisHashKey().substring(key.redisHashKey().indexOf('{'), key.redisHashKey().indexOf('}') + 1);
        String hintTag = key.redisHintKey().substring(key.redisHintKey().indexOf('{'), key.redisHintKey().indexOf('}') + 1);
        assertThat(hashTag).isEqualTo(hintTag).isEqualTo("{cell-1:tenant-alpha:ns-user-123}");
    }

    @Test
    @DisplayName("Req R2.2: Untenanted key uses sentinel and defaults cellId if null")
    void untenantedKeyFormatting() {
        RoutingKey key = new RoutingKey(null, null, "ns-untenanted");

        assertThat(key.redisHashKey())
                .isEqualTo("rt:ns:{default:__NULL_TENANT__:ns-untenanted}");
        assertThat(key.redisHintKey())
                .isEqualTo("rt:ns:{default:__NULL_TENANT__:ns-untenanted}:hint");
    }

    @Test
    @DisplayName("Req R4.1 & Design §3: Cell invalidation and ring keys format correctly")
    void cellKeysFormat() {
        assertThat(RoutingKey.redisCellRingKey("cell-alpha"))
                .isEqualTo("rt:cell:{cell-alpha}:ring");
        assertThat(RoutingKey.redisInvalidationChannel("cell-alpha"))
                .isEqualTo("rt:cell:{cell-alpha}:invalidation");

        assertThat(RoutingKey.redisCellRingKey(null))
                .isEqualTo("rt:cell:{default}:ring");
        assertThat(RoutingKey.redisInvalidationChannel(""))
                .isEqualTo("rt:cell:{default}:invalidation");
    }

    @Test
    @DisplayName("Req R2.5: Unsafe identifiers fail closed before entering key")
    void unsafeIdentifiersFailClosed() {
        assertThatThrownBy(() -> new RoutingKey("cell-1", "tenant/../bad", "ns-123"))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);

        assertThatThrownBy(() -> new RoutingKey("cell-1", "tenant-1", "ns/../escape"))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);

        assertThatThrownBy(() -> new RoutingKey("cell-1", "TENANT_UPPER", "ns-123"))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);

        // G46: Reject '{', '}', ':' in namespace, tenant, and cell
        assertThatThrownBy(() -> new RoutingKey("cell-1", "tenant-1", "ns{123}"))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);
        assertThatThrownBy(() -> new RoutingKey("cell-1", "a}b", "ns-123"))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);
        assertThatThrownBy(() -> new RoutingKey("cell:1", "tenant-1", "ns-123"))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);
        assertThatThrownBy(() -> new RoutingKey("cell{1}", "tenant-1", "ns-123"))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);
        assertThatThrownBy(() -> new RoutingKey("cell/1", "tenant-1", "ns-123"))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);
    }

    @Test
    @DisplayName("G44: Null/blank cellId normalizes to 'default' and equals instance with 'default'")
    void cellIdNormalizationAndEquality() {
        RoutingKey keyNull = new RoutingKey(null, "tenant-1", "ns-123");
        RoutingKey keyBlank = new RoutingKey("   ", "tenant-1", "ns-123");
        RoutingKey keyExplicit = new RoutingKey("default", "tenant-1", "ns-123");

        assertThat(keyNull.cellId()).isEqualTo("default");
        assertThat(keyBlank.cellId()).isEqualTo("default");
        assertThat(keyNull).isEqualTo(keyExplicit);
        assertThat(keyBlank).isEqualTo(keyExplicit);
    }

    @Test
    @DisplayName("G44: fromKeyMaterial round-trip preserves tenancy and namespace")
    void fromKeyMaterialRoundTrip() {
        RoutingKey tenanted = RoutingKey.ofTenanted("cell-1", "acme", "ns-99");
        RoutingKey fromTenanted = RoutingKey.fromKeyMaterial("cell-1", tenanted.keyMaterial());
        assertThat(fromTenanted).isEqualTo(tenanted);

        RoutingKey untenanted = RoutingKey.ofUntenanted("cell-1", "ns-99");
        RoutingKey fromUntenanted = RoutingKey.fromKeyMaterial("cell-1", untenanted.keyMaterial());
        assertThat(fromUntenanted).isEqualTo(untenanted);
    }
}
