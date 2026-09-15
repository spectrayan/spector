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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Default immutable implementation of {@link PathwayContext}.
 */
public final class DefaultPathwayContext implements PathwayContext {

    private final String conductionId;
    private final String namespaceId;
    private final PathwayCatalog catalog;
    private final ConductionScope scope;
    private final boolean traceEnabled;
    private final Map<Class<?>, Object> services;
    private final Map<Key<?>, Object> keyedServices;
    private final AttributeBag bag;
    private final ConductionOutcome outcome;

    private DefaultPathwayContext(
            final String conductionId,
            final String namespaceId,
            final PathwayCatalog catalog,
            final ConductionScope scope,
            final boolean traceEnabled,
            final Map<Class<?>, Object> services,
            final Map<Key<?>, Object> keyedServices,
            final AttributeBag bag,
            final ConductionOutcome outcome) {
        this.conductionId = conductionId != null ? conductionId : UUID.randomUUID().toString();
        this.namespaceId = namespaceId;
        this.catalog = catalog;
        this.scope = scope != null ? scope : new ConductionScope();
        this.traceEnabled = traceEnabled;
        this.services = Map.copyOf(services);
        this.keyedServices = Map.copyOf(keyedServices);
        this.bag = bag != null ? bag : AttributeBag.create();
        this.outcome = outcome != null ? outcome : new ConductionOutcome();
    }

    @Override
    public String conductionId() {
        return conductionId;
    }

    @Override
    public String namespaceId() {
        return namespaceId;
    }

    @Override
    public PathwayCatalog catalog() {
        return catalog;
    }

    @Override
    public ConductionScope scope() {
        return scope;
    }

    @Override
    public boolean traceEnabled() {
        return traceEnabled;
    }

    @Override
    public <T> T get(final Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        final Object service = services.get(type);
        if (service == null) {
            throw new CognitivePathwayException(
                    ErrorCode.MEMORY_PATHWAY_FAILED,
                    "PathwayContext",
                    "get",
                    FaultKind.CONTRACT,
                    false,
                    new IllegalArgumentException("Required service not registered: " + type.getName()));
        }
        if (service instanceof Supplier<?> supplier && !Supplier.class.isAssignableFrom(type)) {
            final Object supplied = supplier.get();
            if (supplied == null) {
                throw new CognitivePathwayException(
                        ErrorCode.MEMORY_PATHWAY_FAILED,
                        "PathwayContext",
                        "get",
                        FaultKind.CONTRACT,
                        false,
                        new IllegalArgumentException("Service supplier returned null for: " + type.getName()));
            }
            return type.cast(supplied);
        }
        return type.cast(service);
    }

    @Override
    public <T> Optional<T> find(final Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        final Object service = services.get(type);
        if (service == null) {
            return Optional.empty();
        }
        if (service instanceof Supplier<?> supplier && !Supplier.class.isAssignableFrom(type)) {
            final Object supplied = supplier.get();
            return Optional.ofNullable(type.cast(supplied));
        }
        return Optional.of(type.cast(service));
    }

    @Override
    public <T> T get(final Key<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        final Object service = keyedServices.get(key);
        if (service == null) {
            throw new CognitivePathwayException(
                    ErrorCode.MEMORY_PATHWAY_FAILED,
                    "PathwayContext",
                    "get",
                    FaultKind.CONTRACT,
                    false,
                    new IllegalArgumentException("Required keyed service not registered: " + key));
        }
        if (service instanceof Supplier<?> supplier && !Supplier.class.isAssignableFrom(key.type())) {
            final Object supplied = supplier.get();
            if (supplied == null) {
                throw new CognitivePathwayException(
                        ErrorCode.MEMORY_PATHWAY_FAILED,
                        "PathwayContext",
                        "get",
                        FaultKind.CONTRACT,
                        false,
                        new IllegalArgumentException("Keyed service supplier returned null for: " + key));
            }
            return key.type().cast(supplied);
        }
        return key.type().cast(service);
    }

    @Override
    public <T> Optional<T> find(final Key<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        final Object service = keyedServices.get(key);
        if (service == null) {
            return Optional.empty();
        }
        if (service instanceof Supplier<?> supplier && !Supplier.class.isAssignableFrom(key.type())) {
            final Object supplied = supplier.get();
            return Optional.ofNullable(key.type().cast(supplied));
        }
        return Optional.of(key.type().cast(service));
    }

    @Override
    public AttributeBag bag() {
        return bag;
    }

    @Override
    public ConductionOutcome outcome() {
        return outcome;
    }

    @Override
    public PathwayContext nested(final String segment) {
        scope.pushSegment(segment);
        return new DefaultPathwayContext(
                conductionId,
                namespaceId,
                catalog,
                scope,
                traceEnabled,
                services,
                keyedServices,
                bag,
                outcome);
    }

    @Override
    public PathwayContext nestedWithOutcome(final String segment, final ConductionOutcome childOutcome) {
        scope.pushSegment(segment);
        return new DefaultPathwayContext(
                conductionId,
                namespaceId,
                catalog,
                scope,
                traceEnabled,
                services,
                keyedServices,
                bag,
                Objects.requireNonNull(childOutcome, "childOutcome cannot be null"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder from(final PathwayContext context) {
        final Builder b = new Builder();
        if (context == null) {
            return b;
        }
        b.conductionId(context.conductionId());
        b.namespaceId(context.namespaceId());
        b.catalog(context.catalog());
        b.scope(context.scope());
        b.traceEnabled(context.traceEnabled());
        b.bag(context.bag());
        b.outcome(context.outcome());
        if (context instanceof DefaultPathwayContext dpc) {
            b.services.putAll(dpc.services);
            b.keyedServices.putAll(dpc.keyedServices);
        }
        return b;
    }

    public static final class Builder {
        private String conductionId;
        private String namespaceId;
        private PathwayCatalog catalog;
        private ConductionScope scope;
        private boolean traceEnabled = false;
        private final Map<Class<?>, Object> services = new HashMap<>();
        private final Map<Key<?>, Object> keyedServices = new HashMap<>();
        private AttributeBag bag;
        private ConductionOutcome outcome;

        public Builder conductionId(final String id) {
            this.conductionId = id;
            return this;
        }

        public Builder namespaceId(final String namespaceId) {
            this.namespaceId = namespaceId;
            return this;
        }

        public Builder catalog(final PathwayCatalog catalog) {
            this.catalog = catalog;
            return this;
        }

        public Builder scope(final ConductionScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder traceEnabled(final boolean enabled) {
            this.traceEnabled = enabled;
            return this;
        }

        public <T> Builder bind(final Class<T> type, final T instance) {
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(instance, "instance cannot be null");
            if (services.containsKey(type)) {
                throw new IllegalStateException("Service class already registered: " + type.getName());
            }
            services.put(type, instance);
            return this;
        }

        public <T> Builder bindIfAbsent(final Class<T> type, final T instance) {
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(instance, "instance cannot be null");
            services.putIfAbsent(type, instance);
            return this;
        }

        public <T> Builder bind(final Key<T> key, final T instance) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(instance, "instance cannot be null");
            if (keyedServices.containsKey(key)) {
                throw new IllegalStateException("Keyed service already registered: " + key);
            }
            keyedServices.put(key, instance);
            return this;
        }

        public <T> Builder bindIfAbsent(final Key<T> key, final T instance) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(instance, "instance cannot be null");
            keyedServices.putIfAbsent(key, instance);
            return this;
        }

        public <T> Builder bindSupplier(final Class<T> type, final Supplier<T> supplier) {
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(supplier, "supplier cannot be null");
            if (services.containsKey(type)) {
                throw new IllegalStateException("Service class already registered: " + type.getName());
            }
            services.put(type, supplier);
            return this;
        }

        public <T> Builder bindSupplierIfAbsent(final Class<T> type, final Supplier<T> supplier) {
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(supplier, "supplier cannot be null");
            services.putIfAbsent(type, supplier);
            return this;
        }

        public <T> Builder bindSupplier(final Key<T> key, final Supplier<T> supplier) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(supplier, "supplier cannot be null");
            if (keyedServices.containsKey(key)) {
                throw new IllegalStateException("Keyed service already registered: " + key);
            }
            keyedServices.put(key, supplier);
            return this;
        }

        public <T> Builder bindSupplierIfAbsent(final Key<T> key, final Supplier<T> supplier) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(supplier, "supplier cannot be null");
            keyedServices.putIfAbsent(key, supplier);
            return this;
        }

        public Builder bag(final AttributeBag bag) {
            this.bag = bag;
            return this;
        }

        public Builder outcome(final ConductionOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public PathwayContext build() {
            return new DefaultPathwayContext(
                    conductionId,
                    namespaceId,
                    catalog,
                    scope,
                    traceEnabled,
                    services,
                    keyedServices,
                    bag,
                    outcome);
        }
    }
}
