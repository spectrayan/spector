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
package com.spectrayan.spector.memory.model;

/**
 * Fluent builder helper for {@link RecallOptions}.
 * Delegates directly to {@link RecallOptions.Builder}.
 */
public final class RecallOptionsBuilder {

    private final RecallOptions.Builder delegate;

    public RecallOptionsBuilder() {
        this.delegate = RecallOptions.builder();
    }

    public RecallOptionsBuilder(RecallOptions.Builder delegate) {
        this.delegate = delegate != null ? delegate : RecallOptions.builder();
    }

    public static RecallOptions.Builder builder() {
        return RecallOptions.builder();
    }

    public RecallOptionsBuilder partitionVisitBudget(int budget) {
        delegate.partitionVisitBudget(budget);
        return this;
    }

    public RecallOptionsBuilder partitionBudget(int budget) {
        delegate.partitionBudget(budget);
        return this;
    }

    public RecallOptions.Builder delegate() {
        return delegate;
    }

    public RecallOptions build() {
        return delegate.build();
    }
}
