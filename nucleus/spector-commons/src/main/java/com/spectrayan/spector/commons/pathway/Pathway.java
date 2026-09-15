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

/**
 * A cognitive pathway representing a composable mental operation with typed input and output.
 *
 * @param <I> the input signal or request type
 * @param <O> the output result or report type
 */
public interface Pathway<I, O> extends AutoCloseable {

    /**
     * Returns the unique identifying name of this pathway.
     *
     * @return pathway name
     */
    String name();

    /**
     * Returns the accepted input type.
     *
     * @return input type class
     */
    Class<I> inputType();

    /**
     * Returns the produced output type.
     *
     * @return output type class
     */
    Class<O> outputType();

    /**
     * Conducts this pathway using the provided input.
     *
     * <p>If {@code input} is a {@link ContextualSignal} and has no context bound,
     * implementations must fail fast or ensure a context is established.</p>
     *
     * @param input the input signal
     * @return output result
     */
    O conduct(I input);

    /**
     * Binds the specified {@link PathwayContext} onto the input (if it is a {@link ContextualSignal})
     * and conducts the pathway.
     *
     * @param ctx   pathway execution context
     * @param input the input signal
     * @return output result
     */
    default O conduct(final PathwayContext ctx, final I input) {
        if (input instanceof ContextualSignal cs) {
            cs.bind(ctx);
        }
        return conduct(input);
    }

    @Override
    default void close() {
        // Default no-op. Pathways override when holding closable resources.
    }
}
