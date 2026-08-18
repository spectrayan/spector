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

import java.util.List;

/**
 * Represents a signal capable of diverging into multiple parallel forks and later merging them back.
 *
 * @param <S> the type of the signal
 */
public interface DivergentCapable<S> {

    /**
     * Creates a thread-safe fork of this signal for parallel execution.
     *
     * @return a new forked signal instance
     */
    S fork();

    /**
     * Merges the results of multiple forks back into this original signal.
     *
     * @param forks the list of completed forks to merge
     */
    void merge(List<S> forks);
}
