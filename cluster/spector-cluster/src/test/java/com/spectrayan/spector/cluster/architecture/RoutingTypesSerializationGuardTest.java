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
package com.spectrayan.spector.cluster.architecture;

import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural guard ensuring Redis holds routing metadata only, never kernel data plane types
 * (Req R3.6, Invariant K6, ADR §8.1, §15.6).
 *
 * <p>Asserts that no bundle, manifest, WAL, or vector type is reachable from {@link RouteBinding}
 * or {@link RoutingKey}.</p>
 */
class RoutingTypesSerializationGuardTest {

    private static final Set<String> FORBIDDEN_TYPE_SNIPPETS = Set.of(
            "bundle", "manifest", "wal", "vector", "embedding", "engram", "partition", "memory"
    );

    @Test
    @DisplayName("Req R3.6 & K6: RouteBinding fields contain no data plane types")
    void routeBindingMustOnlyContainRoutingMetadata() {
        assertClassFieldsAreClean(RouteBinding.class);
    }

    @Test
    @DisplayName("Req R3.6 & K6: RoutingKey fields contain no data plane types")
    void routingKeyMustOnlyContainRoutingMetadata() {
        assertClassFieldsAreClean(RoutingKey.class);
    }

    private void assertClassFieldsAreClean(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            String typeName = field.getType().getName().toLowerCase();
            for (String snippet : FORBIDDEN_TYPE_SNIPPETS) {
                assertThat(typeName)
                        .withFailMessage("Field %s in %s contains forbidden data plane type: %s",
                                field.getName(), clazz.getSimpleName(), field.getType().getName())
                        .doesNotContain(snippet);
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            String returnTypeName = method.getReturnType().getName().toLowerCase();
            for (String snippet : FORBIDDEN_TYPE_SNIPPETS) {
                assertThat(returnTypeName)
                        .withFailMessage("Method %s in %s returns forbidden data plane type: %s",
                                method.getName(), clazz.getSimpleName(), method.getReturnType().getName())
                        .doesNotContain(snippet);
            }
        }
    }
}
