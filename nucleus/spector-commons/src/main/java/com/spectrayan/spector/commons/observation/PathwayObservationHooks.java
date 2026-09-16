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
package com.spectrayan.spector.commons.observation;

import com.spectrayan.spector.commons.pathway.PathwayContext;

import java.util.Optional;

/**
 * Accessor and global registration holder for {@link PathwayObservationHook}.
 */
public final class PathwayObservationHooks {

    private static volatile PathwayObservationHook globalHook = PathwayObservationHook.NOOP;

    private PathwayObservationHooks() {}

    /**
     * Sets the global {@link PathwayObservationHook}.
     *
     * @param hook the observation hook, or null to reset to NOOP
     */
    public static void setGlobal(PathwayObservationHook hook) {
        globalHook = hook != null ? hook : PathwayObservationHook.NOOP;
    }

    /**
     * Returns the global {@link PathwayObservationHook}.
     *
     * @return active global hook
     */
    public static PathwayObservationHook global() {
        return globalHook;
    }

    /**
     * Resolves the effective {@link PathwayObservationHook} for a given {@link PathwayContext},
     * falling back to the global hook if none is bound in the context.
     *
     * @param ctx the pathway context (may be null)
     * @return the resolved hook (never null)
     */
    public static PathwayObservationHook get(PathwayContext ctx) {
        if (ctx != null) {
            Optional<PathwayObservationHook> local = ctx.find(PathwayObservationHook.class);
            if (local.isPresent()) {
                return local.get();
            }
        }
        return globalHook;
    }
}
