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
package com.spectrayan.spector.kernel.bundle;

import com.spectrayan.spector.kernel.region.RegionId;

import com.spectrayan.spector.kernel.layout.RegionLayout;
import com.spectrayan.spector.kernel.shape.AppendMemory;
import com.spectrayan.spector.kernel.shape.ChainMemory;
import com.spectrayan.spector.kernel.shape.GraphMemory;
import com.spectrayan.spector.kernel.shape.HashTableMemory;
import com.spectrayan.spector.kernel.shape.RecordMemory;
import com.spectrayan.spector.kernel.shape.RegistryMemory;

import java.util.Optional;

/**
 * Factory contract for opening bundle regions as strongly-typed shape memories.
 *
 * <p>Replaces untyped byte slice vending ({@code regionSegment}) with shape contracts
 * ({@link RecordMemory}, {@link AppendMemory}, {@link GraphMemory}, {@link ChainMemory},
 * {@link HashTableMemory}, {@link RegistryMemory}).</p>
 */
public interface RegionOpener {

    /**
     * Opens a record-shaped region with the specified layout.
     *
     * @param id     the region identifier
     * @param layout the region layout
     * @param <L>    the layout type
     * @return a live {@link RecordMemory} handle for the region
     */
    <L extends RegionLayout> RecordMemory<L> openRecord(RegionId id, L layout);

    /**
     * Opens an append-shaped region with the specified layout.
     *
     * @param id     the region identifier
     * @param layout the region layout
     * @param <L>    the layout type
     * @return a live {@link AppendMemory} handle for the region
     */
    <L extends RegionLayout> AppendMemory<L> openAppend(RegionId id, L layout);

    /**
     * Opens a graph-shaped region with the specified layout.
     *
     * @param id     the region identifier
     * @param layout the region layout
     * @param <L>    the layout type
     * @return a live {@link GraphMemory} handle for the region
     */
    <L extends RegionLayout> GraphMemory<L> openGraph(RegionId id, L layout);

    /**
     * Opens a chain-shaped region with the specified layout.
     *
     * @param id     the region identifier
     * @param layout the region layout
     * @param <L>    the layout type
     * @return a live {@link ChainMemory} handle for the region
     */
    <L extends RegionLayout> ChainMemory<L> openChain(RegionId id, L layout);

    /**
     * Opens a hash-table-shaped region with the specified layout.
     *
     * @param id     the region identifier
     * @param layout the region layout
     * @param <L>    the layout type
     * @return a live {@link HashTableMemory} handle for the region
     */
    <L extends RegionLayout> HashTableMemory<L> openHashTable(RegionId id, L layout);

    /**
     * Opens a registry-shaped region.
     *
     * @param id the region identifier
     * @return a live {@link RegistryMemory} handle for the region
     */
    RegistryMemory openRegistry(RegionId id);

    /**
     * Attempts to open a record-shaped region, returning {@link Optional#empty()} if absent.
     */
    <L extends RegionLayout> Optional<RecordMemory<L>> tryOpenRecord(RegionId id, L layout);

    /**
     * Attempts to open an append-shaped region, returning {@link Optional#empty()} if absent.
     */
    <L extends RegionLayout> Optional<AppendMemory<L>> tryOpenAppend(RegionId id, L layout);

    /**
     * Attempts to open a graph-shaped region, returning {@link Optional#empty()} if absent.
     */
    <L extends RegionLayout> Optional<GraphMemory<L>> tryOpenGraph(RegionId id, L layout);

    /**
     * Attempts to open a chain-shaped region, returning {@link Optional#empty()} if absent.
     */
    <L extends RegionLayout> Optional<ChainMemory<L>> tryOpenChain(RegionId id, L layout);

    /**
     * Attempts to open a hash-table-shaped region, returning {@link Optional#empty()} if absent.
     */
    <L extends RegionLayout> Optional<HashTableMemory<L>> tryOpenHashTable(RegionId id, L layout);

    /**
     * Attempts to open a registry-shaped region, returning {@link Optional#empty()} if absent.
     */
    Optional<RegistryMemory> tryOpenRegistry(RegionId id);

    /**
     * Returns whether the specified region exists in this bundle.
     *
     * @param id the region identifier
     * @return true if the region is present, false otherwise
     */
    boolean hasRegion(RegionId id);
}
