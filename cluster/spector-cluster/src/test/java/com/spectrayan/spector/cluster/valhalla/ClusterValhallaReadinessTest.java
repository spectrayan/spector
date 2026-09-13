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
package com.spectrayan.spector.cluster.valhalla;

import com.spectrayan.spector.cluster.fencing.FenceToken;
import com.spectrayan.spector.cluster.routing.ResolvedRoute;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.commons.valhalla.ValueClassValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ClusterValhallaReadinessTest {

    @Test
    @DisplayName("Spector Cluster value candidates pass JEP 390 / JEP 401 compliance audit")
    void testClusterValueCandidates() {
        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(RoutingKey.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(FenceToken.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(ResolvedRoute.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(RouteBinding.class))
                .doesNotThrowAnyException();
    }
}
