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

import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Routing Key, Binding, and Node Identity Tests")
class RoutingTypesTest {

    @Test
    @DisplayName("Tenanted routing key formats canonical keyMaterial")
    void tenantedRoutingKey() {
        RoutingKey key = RoutingKey.ofTenanted("cell-1", "acme-corp", "018f9b8c000070008000000000000001");
        assertThat(key.cellId()).isEqualTo("cell-1");
        assertThat(key.tenantId()).isEqualTo("acme-corp");
        assertThat(key.namespaceId()).isEqualTo("018f9b8c000070008000000000000001");
        assertThat(key.keyMaterial()).isEqualTo("acme-corp/018f9b8c000070008000000000000001");
    }

    @Test
    @DisplayName("Untenanted routing key uses __NULL_TENANT__ sentinel")
    void untenantedRoutingKey() {
        RoutingKey key = RoutingKey.ofUntenanted("cell-1", "default-ns");
        assertThat(key.tenantId()).isNull();
        assertThat(key.keyMaterial()).isEqualTo(RoutingKey.NULL_TENANT_SENTINEL + "/default-ns");
    }

    @Test
    @DisplayName("Invalid identifiers fail closed with SpectorValidationException")
    void invalidIdentifiersFailClosed() {
        // Blank namespace
        assertThatThrownBy(() -> new RoutingKey("cell-1", "tenant1", ""))
                .isInstanceOf(SpectorValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID);

        // Path traversal / slash in namespace
        assertThatThrownBy(() -> new RoutingKey("cell-1", "tenant1", "ns/invalid"))
                .isInstanceOf(SpectorValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID);

        // Dot in namespace
        assertThatThrownBy(() -> new RoutingKey("cell-1", "tenant1", "ns..invalid"))
                .isInstanceOf(SpectorValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID);

        // Uppercase tenant fails (APFS case-collision defense)
        assertThatThrownBy(() -> new RoutingKey("cell-1", "Tenant-Uppercase", "ns1"))
                .isInstanceOf(SpectorValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID);
    }

    @Test
    @DisplayName("RouteBinding records routing decisions")
    void routeBindingCreation() {
        RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant1", "ns1");
        RouteBinding binding = RouteBinding.ofHash(key, "node-1", 42L);

        assertThat(binding.key()).isEqualTo(key);
        assertThat(binding.ownerId()).isEqualTo("node-1");
        assertThat(binding.epoch()).isEqualTo(42L);
        assertThat(binding.mode()).isEqualTo(RouteMode.HASH);
        assertThat(binding.fence()).isNull();
        assertThat(binding.hwm()).isNull();
    }

    @Test
    @DisplayName("NodeIdentity enforces cellId and nodeId for cluster roles")
    void nodeIdentityRequirements() {
        // Standalone succeeds with nulls
        NodeIdentity standalone = NodeIdentity.standalone();
        assertThat(standalone.role()).isEqualTo(NodeRole.STANDALONE);

        // Owner fails with blank cellId or nodeId
        assertThatThrownBy(() -> new NodeIdentity(null, "node-1", NodeRole.OWNER))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new NodeIdentity("cell-1", null, NodeRole.OWNER))
                .isInstanceOf(SpectorValidationException.class);

        // Valid owner succeeds
        NodeIdentity owner = new NodeIdentity("cell-1", "node-1", NodeRole.OWNER);
        assertThat(owner.cellId()).isEqualTo("cell-1");
        assertThat(owner.nodeId()).isEqualTo("node-1");
        assertThat(owner.role()).isEqualTo(NodeRole.OWNER);
    }
}
