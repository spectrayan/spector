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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link PathwayCatalog} backed by a thread-safe map.
 */
public final class DefaultPathwayCatalog implements PathwayCatalog {

    private final Map<Class<?>, Pathway<?, ?>> pathways = new ConcurrentHashMap<>();

    @Override
    public <I, O> void register(final Class<? extends Pathway<I, O>> type, final Pathway<I, O> instance) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(instance, "instance cannot be null");
        final Pathway<?, ?> existing = pathways.putIfAbsent(type, instance);
        if (existing != null) {
            throw new IllegalStateException("Pathway type already registered: " + type.getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I, O> Optional<Pathway<I, O>> find(final Class<? extends Pathway<I, O>> type) {
        Objects.requireNonNull(type, "type cannot be null");
        final Pathway<?, ?> pathway = pathways.get(type);
        return pathway != null ? Optional.of((Pathway<I, O>) pathway) : Optional.empty();
    }

    @Override
    public <I, O> Pathway<I, O> require(final Class<? extends Pathway<I, O>> type) {
        return this.<I, O>find(type).orElseThrow(() -> new CognitivePathwayException(
                ErrorCode.MEMORY_PATHWAY_FAILED,
                "PathwayCatalog",
                "require",
                FaultKind.CONTRACT,
                false,
                new IllegalStateException("Pathway not registered in catalog: " + type.getName())));
    }

    @Override
    public <I, O> O invoke(final Class<? extends Pathway<I, O>> type,
                           final PathwayContext ctx,
                           final I input) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(ctx, "ctx cannot be null");
        final Pathway<I, O> pathway = require(type);
        ctx.scope().assertNotOnStack(pathway.name());
        return pathway.conduct(ctx.nested(pathway.name()), input);
    }

    @Override
    public Collection<Pathway<?, ?>> all() {
        return Collections.unmodifiableCollection(pathways.values());
    }
}
