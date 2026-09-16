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
/**
 * Provides byte-layout constants for store structures that are fixed rather than pluggable,
 * replacing magic offsets that were once scattered across store method bodies.
 *
 * <p>A pluggable per-record layout belongs in {@link com.spectrayan.spector.kernel.layout} as a
 * {@link RegionLayout}. The constants here describe structure that no subclass may vary, so a
 * small {@code final} constants class is the honest model.</p>
 *
 * <p>{@link AdjacencyListFields} defines the structural wiring shared by every
 * {@code AdjacencyListGraphMemory} subclass (#435, TD-14): the vertex record
 * ({@code edgeHead}, {@code degree}, {@code flags}) and the universal two-field prefix
 * ({@code target}, {@code next}) that threads a per-vertex singly linked list through the
 * shared edge slab. The per-edge payload after that prefix still comes from the subclass's own
 * {@code RegionLayout}. {@link CoActivationMetadataFields} defines the checkpoint region, tag
 * dictionary framing, and bandit statistics record used when {@code CoActivationMemory}
 * saves and reloads its hash tables.</p>
 *
 * <p>Key components include {@link AdjacencyListFields} and
 * {@link CoActivationMetadataFields}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.store.field;

import com.spectrayan.spector.kernel.layout.RegionLayout;
