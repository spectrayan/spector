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
package com.spectrayan.spector.commons.cache;

/**
 * Functional interface for handling exceptions during programmatic {@link SpectorCache} operations.
 *
 * <p>Enables non-blocking resilience so cache read or write failures (e.g. Redis network glitch)
 * do not break critical memory recall, ingestion, or graph query workflows.</p>
 */
@FunctionalInterface
public interface SpectorCacheErrorHandler {

    /**
     * Invoked when a cache operation throws an unhandled exception.
     *
     * @param cacheName name of the cache
     * @param operation operation type (e.g. {@code get}, {@code put}, {@code evict}, {@code clear})
     * @param key       cache key involved (or {@code "*"} for full clear)
     * @param error     thrown exception
     */
    void onCacheError(String cacheName, String operation, String key, Throwable error);

    /**
     * Default log-and-continue error handler using JDK System.Logger.
     */
    SpectorCacheErrorHandler LOGGING = (cacheName, operation, key, error) ->
            System.getLogger("com.spectrayan.spector.cache").log(
                    System.Logger.Level.WARNING,
                    "Cache error on cache='{0}', operation='{1}', key='{2}': {3}",
                    cacheName, operation, key, error != null ? error.getMessage() : "unknown");

    /**
     * Strict error handler that wraps and rethrows the exception (useful for test harnesses).
     */
    SpectorCacheErrorHandler STRICT = (cacheName, operation, key, error) -> {
        if (error instanceof RuntimeException re) {
            throw re;
        }
        throw new IllegalStateException("Cache error on cache=" + cacheName + " key=" + key, error);
    };
}
