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
package com.spectrayan.spector.commons.pathway;

import com.spectrayan.spector.commons.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PathwayContext")
class PathwayContextTest {

    interface DummyService {
        String name();
    }

    static class DummyServiceImpl implements DummyService {
        @Override
        public String name() {
            return "dummy";
        }
    }

    private static final Key<String> CONFIG_KEY = Key.of("config", String.class);

    @Test
    @DisplayName("Binds and retrieves typed services")
    void bindAndRetrieveServices() {
        var catalog = new DefaultPathwayCatalog();
        var service = new DummyServiceImpl();

        var ctx = DefaultPathwayContext.builder()
                .conductionId("test-conduction-1")
                .namespaceId("ns-alpha")
                .catalog(catalog)
                .traceEnabled(true)
                .bind(DummyService.class, service)
                .bind(CONFIG_KEY, "test-config-val")
                .build();

        assertThat(ctx.conductionId()).isEqualTo("test-conduction-1");
        assertThat(ctx.namespaceId()).isEqualTo("ns-alpha");
        assertThat(ctx.catalog()).isSameAs(catalog);
        assertThat(ctx.traceEnabled()).isTrue();

        assertThat(ctx.get(DummyService.class)).isSameAs(service);
        assertThat(ctx.find(DummyService.class)).contains(service);

        assertThat(ctx.get(CONFIG_KEY)).isEqualTo("test-config-val");
        assertThat(ctx.find(CONFIG_KEY)).contains("test-config-val");
    }

    @Test
    @DisplayName("Throws IllegalStateException on duplicate service registration")
    void duplicateServiceThrows() {
        var service1 = new DummyServiceImpl();
        var service2 = new DummyServiceImpl();

        var builder = DefaultPathwayContext.builder();
        builder.bind(DummyService.class, service1);

        assertThatThrownBy(() -> builder.bind(DummyService.class, service2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");

        builder.bind(CONFIG_KEY, "val1");
        assertThatThrownBy(() -> builder.bind(CONFIG_KEY, "val2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Throws CognitivePathwayException with CONTRACT when service is absent on get")
    void throwsContractExceptionOnAbsentService() {
        var ctx = DefaultPathwayContext.builder().build();

        assertThatThrownBy(() -> ctx.get(DummyService.class))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.errorCode()).isEqualTo(ErrorCode.MEMORY_PATHWAY_FAILED);
                    assertThat(cpe.kind()).isEqualTo(FaultKind.CONTRACT);
                });

        assertThatThrownBy(() -> ctx.get(CONFIG_KEY))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.errorCode()).isEqualTo(ErrorCode.MEMORY_PATHWAY_FAILED);
                    assertThat(cpe.kind()).isEqualTo(FaultKind.CONTRACT);
                });
    }

    @Test
    @DisplayName("nested() shares ConductionScope by reference, pushes segment, shares bag and outcome")
    void nestedSharesScopeAndBag() {
        var bag = AttributeBag.create();
        bag.put(CONFIG_KEY, "shared-data");

        var outcome = new ConductionOutcome();
        var rootScope = new ConductionScope();

        var rootCtx = DefaultPathwayContext.builder()
                .conductionId("root-123")
                .scope(rootScope)
                .bag(bag)
                .outcome(outcome)
                .build();

        var nestedCtx = rootCtx.nested("dream_ingest");

        assertThat(nestedCtx.conductionId()).isEqualTo("root-123");
        assertThat(nestedCtx.scope()).isSameAs(rootScope);
        assertThat(nestedCtx.bag()).isSameAs(bag);
        assertThat(nestedCtx.outcome()).isSameAs(outcome);

        nestedCtx.scope().enter("dream");
        assertThat(nestedCtx.scope().segment()).isEqualTo("dream_ingest");
        nestedCtx.scope().leave("dream");
    }

    @Test
    @DisplayName("from() copies all context properties and supports bindIfAbsent()")
    void fromCopiesPropertiesAndBindIfAbsent() {
        var catalog = new DefaultPathwayCatalog();
        var service1 = new DummyServiceImpl();
        var service2 = new DummyServiceImpl();

        var orig = DefaultPathwayContext.builder()
                .conductionId("orig-id")
                .namespaceId("ns-test")
                .catalog(catalog)
                .traceEnabled(true)
                .bind(DummyService.class, service1)
                .bind(CONFIG_KEY, "val1")
                .build();

        var copied = DefaultPathwayContext.from(orig)
                .bindIfAbsent(DummyService.class, service2)
                .bindIfAbsent(CONFIG_KEY, "val2")
                .build();

        assertThat(copied.conductionId()).isEqualTo("orig-id");
        assertThat(copied.namespaceId()).isEqualTo("ns-test");
        assertThat(copied.catalog()).isSameAs(catalog);
        assertThat(copied.traceEnabled()).isTrue();
        assertThat(copied.get(DummyService.class)).isSameAs(service1);
        assertThat(copied.get(CONFIG_KEY)).isEqualTo("val1");
    }

    @Test
    @DisplayName("bindSupplier dynamically evaluates supplier and reflects mutations")
    void bindSupplierEvaluatesDynamically() {
        var mutableHolder = new java.util.concurrent.atomic.AtomicReference<>("initial-value");

        var ctx = DefaultPathwayContext.builder()
                .conductionId("dyn-id")
                .bindSupplier(String.class, mutableHolder::get)
                .bindSupplier(CONFIG_KEY, mutableHolder::get)
                .build();

        assertThat(ctx.get(String.class)).isEqualTo("initial-value");
        assertThat(ctx.find(String.class)).contains("initial-value");
        assertThat(ctx.get(CONFIG_KEY)).isEqualTo("initial-value");
        assertThat(ctx.find(CONFIG_KEY)).contains("initial-value");

        // Mutate dynamic value
        mutableHolder.set("updated-value");

        assertThat(ctx.get(String.class)).isEqualTo("updated-value");
        assertThat(ctx.find(String.class)).contains("updated-value");
        assertThat(ctx.get(CONFIG_KEY)).isEqualTo("updated-value");
        assertThat(ctx.find(CONFIG_KEY)).contains("updated-value");
    }

    @Test
    @DisplayName("bindSupplier throws CONTRACT when supplier returns null on get")
    void bindSupplierThrowsWhenSupplierReturnsNull() {
        var ctx = DefaultPathwayContext.builder()
                .conductionId("null-supplier-id")
                .bindSupplier(String.class, () -> null)
                .build();

        assertThat(ctx.find(String.class)).isEmpty();
        assertThatThrownBy(() -> ctx.get(String.class))
                .isInstanceOf(CognitivePathwayException.class)
                .hasMessageContaining("Service supplier returned null");
    }
}
