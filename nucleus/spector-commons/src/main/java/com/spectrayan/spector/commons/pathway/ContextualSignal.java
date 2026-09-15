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
 * A signal that carries a {@link PathwayContext} for runtime services and execution tracing.
 */
public interface ContextualSignal extends TraceableSignal {

    /**
     * Returns the context bound to this signal, or null if unbound.
     *
     * @return pathway context
     */
    PathwayContext context();

    /**
     * Binds (or re-binds) the context to this signal.
     *
     * <p>Must be idempotent-overwrite: retrying or re-conducting with a fresh context
     * should successfully overwrite any prior context.</p>
     *
     * @param ctx pathway context to bind
     */
    void bind(PathwayContext ctx);
}
