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

import java.util.Optional;

/**
 * Execution context for cognitive pathway conduction.
 *
 * <p>Carries shared services, process and namespace identity, per-conduction scratchpad
 * attributes, and outcome reporting metadata across the pathway execution graph.</p>
 */
public interface PathwayContext {

    /**
     * Returns the unique identifier for the root conduction.
     *
     * @return conduction id
     */
    String conductionId();

    /**
     * Returns the namespace identifier, or null for process-global operations.
     *
     * @return namespace id
     */
    String namespaceId();

    /**
     * Returns the pathway catalog used for discovering and invoking nested pathways.
     *
     * @return catalog
     */
    PathwayCatalog catalog();

    /**
     * Returns the conduction scope tracking the execution stack and cycle detection.
     *
     * @return scope
     */
    ConductionScope scope();

    /**
     * Returns whether execution tracing is enabled for this conduction.
     *
     * @return true if trace enabled
     */
    boolean traceEnabled();

    /**
     * Returns the registered service of the specified class type, or throws
     * {@link CognitivePathwayException} with {@link FaultKind#CONTRACT} if absent.
     *
     * @param type service class
     * @param <T>  service type
     * @return service instance
     * @throws CognitivePathwayException if service is not found
     */
    <T> T get(Class<T> type);

    /**
     * Finds the registered service of the specified class type.
     *
     * @param type service class
     * @param <T>  service type
     * @return optional containing the service if registered
     */
    <T> Optional<T> find(Class<T> type);

    /**
     * Returns the registered service for the specified key, or throws
     * {@link CognitivePathwayException} with {@link FaultKind#CONTRACT} if absent.
     *
     * @param key typed key
     * @param <T> service type
     * @return service instance
     * @throws CognitivePathwayException if key is not found
     */
    <T> T get(Key<T> key);

    /**
     * Finds the registered service for the specified key.
     *
     * @param key typed key
     * @param <T> service type
     * @return optional containing the service if registered
     */
    <T> Optional<T> find(Key<T> key);

    /**
     * Returns the attribute bag for scratchpad data.
     *
     * @return attribute bag
     */
    AttributeBag bag();

    /**
     * Returns the conduction outcome tracking completion, degraded marks, and bypasses.
     *
     * @return outcome
     */
    ConductionOutcome outcome();

    /**
     * Creates a child context for a nested pathway invocation.
     *
     * <p>Shares the catalog, services, attribute bag, outcome, and the same {@link ConductionScope}
     * instance by reference. Associates {@code segment} as a new frame on the shared scope.</p>
     *
     * @param segment nested pathway or stage segment name
     * @return child context
     */
    PathwayContext nested(String segment);

    /**
     * Creates a child context for a nested pathway invocation with an isolated
     * {@link ConductionOutcome}. Shares the catalog, services, attribute bag, and
     * the same {@link ConductionScope} instance by reference.
     *
     * <p>The caller is responsible for calling
     * {@code outcome().importFrom(childOutcome, prefix)} after the nested pathway completes.</p>
     *
     * @param segment      nested pathway or stage segment name
     * @param childOutcome the isolated outcome for the nested conduction
     * @return child context with the provided outcome
     */
    PathwayContext nestedWithOutcome(String segment, ConductionOutcome childOutcome);
}
