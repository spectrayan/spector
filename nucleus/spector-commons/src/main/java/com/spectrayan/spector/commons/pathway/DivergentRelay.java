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
import com.spectrayan.spector.commons.error.SpectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * A relay that diverges a capable signal across multiple parallel branches with per-branch error policies.
 *
 * @param <S> the type of the signal
 */
public final class DivergentRelay<S> implements SynapticRelay<S> {

    private static final Logger log = LoggerFactory.getLogger(DivergentRelay.class);

    private final String name;
    private final List<CognitivePathway.RelayEntry<S>> branches;

    /**
     * Constructs a DivergentRelay with uniform FAIL_FAST policies for all branches.
     *
     * @param name     the name of the relay
     * @param branches the parallel branches to execute
     */
    public DivergentRelay(final String name, final List<SynapticRelay<S>> branches) {
        this(name, branches.stream()
                .map(b -> new CognitivePathway.RelayEntry<>(b, ErrorPolicy.FAIL_FAST))
                .toList(), true);
    }

    /**
     * Constructs a DivergentRelay with explicit per-branch entries (relay + error policy).
     *
     * @param name     the name of the relay
     * @param branches the parallel branch entries
     */
    public DivergentRelay(final String name, final List<CognitivePathway.RelayEntry<S>> branches, final boolean isEntryList) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.branches = List.copyOf(branches);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean transmit(final S signal) throws Exception {
        if (!(signal instanceof DivergentCapable)) {
            throw new IllegalArgumentException("Signal must implement DivergentCapable for DivergentRelay");
        }

        final DivergentCapable<S> divergentCapable = (DivergentCapable<S>) signal;
        final List<S> successfulForks = Collections.synchronizedList(new ArrayList<>());
        final List<Callable<Void>> tasks = new ArrayList<>();

        for (final CognitivePathway.RelayEntry<S> branch : branches) {
            final S fork = divergentCapable.fork();
            tasks.add(() -> {
                try {
                    final boolean shouldContinue = branch.relay().transmit(fork);
                    if (shouldContinue) {
                        successfulForks.add(fork);
                    }
                } catch (final Exception e) {
                    if (branch.errorPolicy() == ErrorPolicy.FAIL_FAST) {
                        if (e instanceof SpectorException se) throw se;
                        if (e.getCause() instanceof SpectorException se) throw se;
                        throw e;
                    } else {
                        log.warn("Divergent branch '{}' in relay '{}' degraded gracefully due to error.",
                                branch.relay().relayName(), name, e);
                    }
                }
                return null;
            });
        }

        try {
            ConcurrentTasks.forkJoinAll(tasks);
        } catch (final ConcurrentExecutionException e) {
            // Rethrow unhandled root causes from FAIL_FAST branches
            final Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof SpectorException se) throw se;
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);
        }

        divergentCapable.merge(new ArrayList<>(successfulForks));
        return true;
    }

    /**
     * Returns the configured branches.
     *
     * @return unmodifiable list of branch entries
     */
    public List<CognitivePathway.RelayEntry<S>> branches() {
        return branches;
    }

    @Override
    public String relayName() {
        return name;
    }
}
