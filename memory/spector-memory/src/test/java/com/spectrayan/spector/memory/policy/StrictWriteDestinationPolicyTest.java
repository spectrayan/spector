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
package com.spectrayan.spector.memory.policy;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StrictWriteDestinationPolicy} — namespace enforcement on writes (#1017).
 */
class StrictWriteDestinationPolicyTest {

    private static final String DEFAULT_NS = "default";
    private static final String EXPLICIT_NS = "agent-alpha";

    @Test
    @DisplayName("Rejects REMEMBER to default namespace")
    void rejectsRememberToDefaultNamespace() {
        var policy = new StrictWriteDestinationPolicy(DEFAULT_NS);
        var request = WriteRequest.remember(DEFAULT_NS, "mem-1", 0L, System.currentTimeMillis());

        assertThatThrownBy(() -> policy.checkWrite(request))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(ex -> {
                    var spex = (SpectorValidationException) ex;
                    assertThat(spex.errorCode()).isEqualTo(ErrorCode.DESTINATION_SCOPE_REQUIRED);
                })
                .hasMessageContaining("namespace")
                .hasMessageContaining("default");
    }

    @Test
    @DisplayName("Allows REMEMBER to explicit namespace")
    void allowsRememberToExplicitNamespace() {
        var policy = new StrictWriteDestinationPolicy(DEFAULT_NS);
        var request = WriteRequest.remember(EXPLICIT_NS, "mem-1", 0L, System.currentTimeMillis());

        assertThatNoException().isThrownBy(() -> policy.checkWrite(request));
    }

    @Test
    @DisplayName("Allows FORGET operations even on default namespace")
    void allowsForgetOnDefaultNamespace() {
        var policy = new StrictWriteDestinationPolicy(DEFAULT_NS);
        var request = WriteRequest.forget(DEFAULT_NS, "mem-1", 0L, System.currentTimeMillis());

        assertThatNoException().isThrownBy(() -> policy.checkWrite(request));
    }

    @Test
    @DisplayName("Allows PURGE operations even on default namespace")
    void allowsPurgeOnDefaultNamespace() {
        var policy = new StrictWriteDestinationPolicy(DEFAULT_NS);
        var request = WriteRequest.purge(DEFAULT_NS, "mem-1", 0L, System.currentTimeMillis());

        assertThatNoException().isThrownBy(() -> policy.checkWrite(request));
    }

    @Test
    @DisplayName("Allows REINFORCE operations even on default namespace")
    void allowsReinforceOnDefaultNamespace() {
        var policy = new StrictWriteDestinationPolicy(DEFAULT_NS);
        var request = WriteRequest.reinforce(DEFAULT_NS, "mem-1", 0L, System.currentTimeMillis());

        assertThatNoException().isThrownBy(() -> policy.checkWrite(request));
    }

    @Test
    @DisplayName("Allows CONSOLIDATE operations even on default namespace")
    void allowsConsolidateOnDefaultNamespace() {
        var policy = new StrictWriteDestinationPolicy(DEFAULT_NS);
        var request = WriteRequest.consolidate(DEFAULT_NS, 0L, System.currentTimeMillis());

        assertThatNoException().isThrownBy(() -> policy.checkWrite(request));
    }

    @Test
    @DisplayName("Uses 'default' when null defaultNamespaceId is provided")
    void handlesNullDefaultNamespaceId() {
        var policy = new StrictWriteDestinationPolicy(null);
        var request = WriteRequest.remember("default", "mem-1", 0L, System.currentTimeMillis());

        assertThatThrownBy(() -> policy.checkWrite(request))
                .isInstanceOf(SpectorValidationException.class);
    }

    @Test
    @DisplayName("Custom default namespace is correctly enforced")
    void customDefaultNamespaceEnforced() {
        var policy = new StrictWriteDestinationPolicy("tenant-pool");
        var request = WriteRequest.remember("tenant-pool", "mem-1", 0L, System.currentTimeMillis());

        assertThatThrownBy(() -> policy.checkWrite(request))
                .isInstanceOf(SpectorValidationException.class);
    }

    @Test
    @DisplayName("toString includes default namespace")
    void toStringIncludesNamespace() {
        var policy = new StrictWriteDestinationPolicy(DEFAULT_NS);
        assertThat(policy.toString()).contains("default");
    }
}
