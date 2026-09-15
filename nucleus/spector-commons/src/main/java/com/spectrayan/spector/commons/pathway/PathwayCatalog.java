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

import java.util.Collection;
import java.util.Optional;

/**
 * Service registry and discovery mechanism for cognitive pathways.
 */
public interface PathwayCatalog {

    /**
     * Registers a pathway instance with its type.
     *
     * @param type     pathway interface or class type
     * @param instance pathway instance
     * @param <I>      input type
     * @param <O>      output type
     * @throws IllegalStateException if a pathway of that type is already registered
     */
    <I, O> void register(Class<? extends Pathway<I, O>> type, Pathway<I, O> instance);

    /**
     * Finds a registered pathway by its type.
     *
     * @param type pathway type
     * @param <I>  input type
     * @param <O>  output type
     * @return optional containing the pathway if found
     */
    <I, O> Optional<Pathway<I, O>> find(Class<? extends Pathway<I, O>> type);

    /**
     * Retrieves a registered pathway by its type, or throws {@link CognitivePathwayException} with
     * {@link FaultKind#CONTRACT} if absent.
     *
     * @param type pathway type
     * @param <I>  input type
     * @param <O>  output type
     * @return pathway instance
     * @throws CognitivePathwayException if not found
     */
    <I, O> Pathway<I, O> require(Class<? extends Pathway<I, O>> type);

    /**
     * Invokes a registered pathway by type within a nested context, asserting no recursion cycles.
     *
     * @param type  pathway type
     * @param ctx   current pathway context
     * @param input input signal
     * @param <I>   input type
     * @param <O>   output type
     * @return output result
     */
    <I, O> O invoke(Class<? extends Pathway<I, O>> type, PathwayContext ctx, I input);

    /**
     * Returns all currently registered pathways.
     *
     * @return unmodifiable collection of registered pathways
     */
    Collection<Pathway<?, ?>> all();
}
