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
package com.spectrayan.spector.batch.reflect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding active reflection job contexts keyed by sweep ID.
 *
 * @since 1.5.0
 */
public final class ReflectJobRegistry {

    private static final Map<String, ReflectJobContext> CONTEXTS = new ConcurrentHashMap<>();

    private ReflectJobRegistry() {}

    public static void register(String sweepId, ReflectJobContext context) {
        if (sweepId != null && context != null) {
            CONTEXTS.put(sweepId, context);
        }
    }

    public static ReflectJobContext get(String sweepId) {
        return (sweepId != null) ? CONTEXTS.get(sweepId) : null;
    }

    public static ReflectJobContext remove(String sweepId) {
        return (sweepId != null) ? CONTEXTS.remove(sweepId) : null;
    }

    public static void clear() {
        CONTEXTS.clear();
    }
}
