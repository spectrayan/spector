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
package com.spectrayan.spector.commons.concurrent;

import com.spectrayan.spector.commons.concurrent.spi.DefaultExecutorProvider;
import com.spectrayan.spector.commons.concurrent.spi.SpectorExecutorProvider;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide locator and registry for the active {@link SpectorExecutorProvider}.
 *
 * <p>Discovery resolution order:
 * <ol>
 *   <li>Explicitly installed provider via {@link #install(SpectorExecutorProvider)}</li>
 *   <li>Provider discovered via {@link ServiceLoader}</li>
 *   <li>Fallback {@link DefaultExecutorProvider#INSTANCE}</li>
 * </ol>
 */
public final class SpectorExecutors {

    private static final System.Logger log = System.getLogger(SpectorExecutors.class.getName());

    private static final AtomicReference<SpectorExecutorProvider> INSTALLED = new AtomicReference<>();

    private static final class Holder {
        static final SpectorExecutorProvider DISCOVERED = discover();

        private static SpectorExecutorProvider discover() {
            try {
                ServiceLoader<SpectorExecutorProvider> loader = ServiceLoader.load(SpectorExecutorProvider.class);
                Iterator<SpectorExecutorProvider> it = loader.iterator();
                if (it.hasNext()) {
                    SpectorExecutorProvider found = it.next();
                    log.log(System.Logger.Level.INFO, "Discovered SpectorExecutorProvider via SPI: {0}", found.describe());
                    return found;
                }
            } catch (Exception e) {
                log.log(System.Logger.Level.WARNING, "Error while discovering SpectorExecutorProvider via ServiceLoader: {0}", e.getMessage());
            }
            return DefaultExecutorProvider.INSTANCE;
        }
    }

    private SpectorExecutors() {}

    /**
     * Explicitly installs the host-provided {@link SpectorExecutorProvider}.
     *
     * @param provider the provider to install
     */
    public static void install(SpectorExecutorProvider provider) {
        if (provider != null) {
            INSTALLED.set(provider);
            log.log(System.Logger.Level.INFO, "Installed SpectorExecutorProvider: {0}", provider.describe());
        }
    }

    /**
     * Resets the installed provider back to undiscovered default state (primarily for tests).
     */
    public static void reset() {
        INSTALLED.set(null);
    }

    /**
     * Returns the currently active {@link SpectorExecutorProvider}.
     *
     * @return active provider
     */
    public static SpectorExecutorProvider current() {
        SpectorExecutorProvider provider = INSTALLED.get();
        if (provider != null) {
            return provider;
        }
        return Holder.DISCOVERED;
    }

    /**
     * Resolves an {@link Executor} directly from the active provider for the declared plane and pool name.
     *
     * @param plane target plane
     * @param name  logical pool name
     * @return executor
     */
    public static Executor executor(ThreadPlane plane, String name) {
        return current().executor(plane, name);
    }

    /**
     * Cooperatively drains all active executors up to the specified budget.
     *
     * @param budget maximum duration allocated for draining
     * @return drain result
     */
    public static com.spectrayan.spector.commons.concurrent.spi.DrainResult drain(java.time.Duration budget) {
        return current().drain(budget);
    }

    /**
     * Cooperatively drains executors matching the specified pool filter up to the allocated budget.
     *
     * @param poolFilter substring filter (e.g. namespace ID)
     * @param budget     maximum duration allocated for draining
     * @return drain result
     */
    public static com.spectrayan.spector.commons.concurrent.spi.DrainResult drain(String poolFilter, java.time.Duration budget) {
        return current().drain(poolFilter, budget);
    }
}
