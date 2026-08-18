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

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;

import java.util.function.Consumer;

/**
 * A relay that performs an asynchronous action on the signal in a fire-and-forget manner.
 *
 * @param <S> the type of the signal
 */
public final class ConsolidationRelay<S> implements SynapticRelay<S> {

    private final String name;
    private final Consumer<S> asyncAction;

    /**
     * Constructs a new ConsolidationRelay.
     *
     * @param name        the name of the relay
     * @param asyncAction the consumer action to dispatch asynchronously
     */
    public ConsolidationRelay(final String name, final Consumer<S> asyncAction) {
        this.name = name;
        this.asyncAction = asyncAction;
    }

    @Override
    public boolean transmit(final S signal) throws Exception {
        ConcurrentTasks.fireAndForget(() -> asyncAction.accept(signal));
        return true;
    }

    @Override
    public String relayName() {
        return name;
    }
}
