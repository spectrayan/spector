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

import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A relay that diverges a capable signal across multiple parallel branches.
 *
 * @param <S> the type of the signal
 */
public final class DivergentRelay<S> implements SynapticRelay<S> {

    private static final Logger log = LoggerFactory.getLogger(DivergentRelay.class);

    private final String name;
    private final List<SynapticRelay<S>> branches;

    /**
     * Constructs a new DivergentRelay.
     *
     * @param name     the name of the relay
     * @param branches the parallel branches to execute
     */
    public DivergentRelay(final String name, final List<SynapticRelay<S>> branches) {
        this.name = name;
        this.branches = List.copyOf(branches);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean transmit(final S signal) throws Exception {
        if (!(signal instanceof DivergentCapable)) {
            throw new IllegalArgumentException("Signal must implement DivergentCapable for DivergentRelay");
        }
        
        final DivergentCapable<S> divergentCapable = (DivergentCapable<S>) signal;
        final List<S> forks = new ArrayList<>();
        final List<Callable<Void>> tasks = new ArrayList<>();

        for (final SynapticRelay<S> branch : branches) {
            final S fork = divergentCapable.fork();
            forks.add(fork);
            tasks.add(() -> {
                branch.transmit(fork);
                return null;
            });
        }

        try {
            ConcurrentTasks.forkJoinAll(tasks);
        } catch (final ConcurrentExecutionException e) {
            log.warn("Parallel execution failed in DivergentRelay '{}', falling back to sequential execution.", name, e);
            for (int i = 0; i < branches.size(); i++) {
                branches.get(i).transmit(forks.get(i));
            }
        }

        divergentCapable.merge(forks);
        return true;
    }

    @Override
    public String relayName() {
        return name;
    }
}
