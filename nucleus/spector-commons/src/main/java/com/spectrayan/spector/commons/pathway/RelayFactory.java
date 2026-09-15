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

/**
 * Factory for resolving or creating synaptic relays at pathway composition time.
 */
@FunctionalInterface
public interface RelayFactory {

    /**
     * Creates or resolves a synaptic relay of the specified type.
     *
     * @param type relay type
     * @param <S>  signal type
     * @return synaptic relay instance, or null if unresolved
     */
    <S> SynapticRelay<S> create(Class<? extends SynapticRelay<S>> type);
}
