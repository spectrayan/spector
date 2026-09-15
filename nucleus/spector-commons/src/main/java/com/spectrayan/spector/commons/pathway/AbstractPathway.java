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

import java.util.Objects;

/**
 * Base implementation of {@link Pathway} wrapping a {@link CognitivePathway} conductor engine.
 *
 * @param <S> input contextual signal type
 * @param <O> output result type
 */
public abstract class AbstractPathway<S extends ContextualSignal, O> implements Pathway<S, O> {

    private final String name;
    private final Class<S> inputType;
    private final Class<O> outputType;
    private CognitivePathway<S> engine;

    protected AbstractPathway(final String name,
                              final Class<S> inputType,
                              final Class<O> outputType,
                              final CognitivePathway<S> engine) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.inputType = Objects.requireNonNull(inputType, "inputType cannot be null");
        this.outputType = Objects.requireNonNull(outputType, "outputType cannot be null");
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
    }

    protected AbstractPathway(final String name,
                              final Class<S> inputType,
                              final Class<O> outputType) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.inputType = Objects.requireNonNull(inputType, "inputType cannot be null");
        this.outputType = Objects.requireNonNull(outputType, "outputType cannot be null");
        this.engine = null;
    }

    protected final void initEngine(final CognitivePathway<S> engine) {
        if (this.engine != null) {
            throw new IllegalStateException("engine already initialized for pathway: " + name);
        }
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final Class<S> inputType() {
        return inputType;
    }

    @Override
    public final Class<O> outputType() {
        return outputType;
    }

    /**
     * Returns the underlying relay conductor engine.
     *
     * @return cognitive pathway engine
     */
    public final CognitivePathway<S> engine() {
        if (engine == null) {
            throw new IllegalStateException("engine has not been initialized for pathway: " + name);
        }
        return engine;
    }

    @Override
    public final O conduct(final S signal) {
        Objects.requireNonNull(signal, name + " signal");
        final PathwayContext ctx = signal.context();
        if (ctx == null) {
            throw new CognitivePathwayException(name, "<entry>", FaultKind.CONTRACT, false,
                    new IllegalStateException(name + " conducted without PathwayContext"));
        }
        final ConductionScope scope = ctx.scope();
        scope.enter(name);
        final long startNanos = System.nanoTime();
        try {
            final CognitivePathway<S> pathwayEngine = engine();
            pathwayEngine.conduct(signal);
            final boolean shortCircuited = scope.shortCircuited(name)
                    || scope.shortCircuited(pathwayEngine.pathwayName());
            final ConductionOutcome.Finish finish = shortCircuited
                    ? ConductionOutcome.Finish.SHORT_CIRCUITED
                    : ConductionOutcome.Finish.COMPLETED;
            ctx.outcome().finish(finish);
            com.spectrayan.spector.commons.observation.PathwayObservationHooks.get(ctx)
                    .onConduct(name, finish.name().toLowerCase(), java.time.Duration.ofNanos(System.nanoTime() - startNanos));
            return project(signal);
        } catch (final RuntimeException e) {
            ctx.outcome().finish(ConductionOutcome.Finish.FAILED);
            com.spectrayan.spector.commons.observation.PathwayObservationHooks.get(ctx)
                    .onConduct(name, "failed", java.time.Duration.ofNanos(System.nanoTime() - startNanos));
            throw PathwayExceptions.wrap(name, e);
        } finally {
            scope.leave(name);
        }
    }

    /**
     * Projects the conducted signal into the pathway's output representation.
     *
     * @param signal conducted signal
     * @return output result
     */
    protected abstract O project(S signal);
}
