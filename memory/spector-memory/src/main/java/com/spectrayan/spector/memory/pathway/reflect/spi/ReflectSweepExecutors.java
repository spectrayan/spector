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
package com.spectrayan.spector.memory.pathway.reflect.spi;

import com.spectrayan.spector.memory.pathway.reflect.spi.local.InProcessReflectSweepExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service registry and discovery mechanism for {@link ReflectSweepExecutor} implementations.
 *
 * @since 1.5.0
 */
public final class ReflectSweepExecutors {

    private static final Logger log = LoggerFactory.getLogger(ReflectSweepExecutors.class);

    public static final String ORCHESTRATOR_PROPERTY = "spector.memory.reflect.orchestrator";

    private static final ReentrantLock INIT_LOCK = new ReentrantLock();
    private static volatile RegistryState state;

    private ReflectSweepExecutors() {
    }

    private static final class RegistryState {
        final List<ReflectSweepExecutor> availableExecutors;
        final ReflectSweepExecutor primaryExecutor;

        RegistryState(List<ReflectSweepExecutor> availableExecutors, ReflectSweepExecutor primaryExecutor) {
            this.availableExecutors = availableExecutors;
            this.primaryExecutor = primaryExecutor;
        }
    }

    /**
     * Obtains the primary registered {@link ReflectSweepExecutor}.
     */
    public static ReflectSweepExecutor getPrimary() {
        return getState().primaryExecutor;
    }

    /**
     * Obtains all available registered executors.
     */
    public static List<ReflectSweepExecutor> getAvailable() {
        return getState().availableExecutors;
    }

    /**
     * Resolves an executor matching {@code name} (case-insensitive), or falls back to the primary executor
     * if {@code name} is null, blank, or not found.
     *
     * @param name name of the desired executor, or null/blank for primary
     * @return the resolved executor
     */
    public static ReflectSweepExecutor getExecutor(String name) {
        if (name == null || name.isBlank()) {
            return getPrimary();
        }
        for (ReflectSweepExecutor exec : getAvailable()) {
            if (exec.name().equalsIgnoreCase(name.trim())) {
                return exec;
            }
        }
        log.warn("ReflectSweepExecutor '{}' not found, falling back to primary '{}'", name, getPrimary().name());
        return getPrimary();
    }

    private static volatile String orchestratorOverride;

    /**
     * Programmatically sets the orchestrator override and invalidates cached state.
     *
     * @param orchestrator name of the executor to select
     */
    public static void setOrchestrator(String orchestrator) {
        INIT_LOCK.lock();
        try {
            orchestratorOverride = orchestrator;
            state = null;
        } finally {
            INIT_LOCK.unlock();
        }
    }

    /**
     * Resets discovered state; forces next call to reload via ServiceLoader.
     */
    public static void reset() {
        INIT_LOCK.lock();
        try {
            orchestratorOverride = null;
            state = null;
        } finally {
            INIT_LOCK.unlock();
        }
    }

    private static RegistryState getState() {
        RegistryState localState = state;
        if (localState != null) {
            return localState;
        }

        INIT_LOCK.lock();
        try {
            if (state != null) {
                return state;
            }
            state = initialize();
            return state;
        } finally {
            INIT_LOCK.unlock();
        }
    }

    private static RegistryState initialize() {
        List<ReflectSweepExecutor> discovered = new ArrayList<>();
        String explicitChoice = orchestratorOverride;

        ServiceLoader<ReflectSweepExecutor> loader = ServiceLoader.load(ReflectSweepExecutor.class);
        for (ReflectSweepExecutor exec : loader) {
            try {
                if (exec.available()) {
                    discovered.add(exec);
                    log.info("Discovered ReflectSweepExecutor: '{}' (priority={})", exec.name(), exec.priority());
                }
            } catch (Throwable t) {
                log.warn("Failed checking availability for executor {}: {}", exec.getClass().getName(), t.getMessage());
            }
        }

        discovered.sort(Comparator.comparingInt(ReflectSweepExecutor::priority).reversed());

        ReflectSweepExecutor primary = null;
        if (explicitChoice != null && !explicitChoice.isBlank()) {
            for (ReflectSweepExecutor exec : discovered) {
                if (exec.name().equalsIgnoreCase(explicitChoice.trim())) {
                    primary = exec;
                    log.info("Selected ReflectSweepExecutor '{}' via configuration override", exec.name());
                    break;
                }
            }
        }

        if (primary == null && !discovered.isEmpty()) {
            primary = discovered.get(0);
        }

        if (primary == null) {
            primary = InProcessReflectSweepExecutor.INSTANCE;
            discovered.add(primary);
        }

        log.info("Active primary ReflectSweepExecutor: '{}'", primary.name());
        return new RegistryState(List.copyOf(discovered), primary);
    }
}
