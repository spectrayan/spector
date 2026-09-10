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
package com.spectrayan.spector.kernel.api;

import com.spectrayan.spector.kernel.layout.CoActivationLayout;
import com.spectrayan.spector.kernel.layout.ContinuityLayout;
import com.spectrayan.spector.kernel.layout.HebbianLayout;
import com.spectrayan.spector.kernel.layout.HyperEntityLayout;
import com.spectrayan.spector.kernel.layout.IndexEntryLayout;
import com.spectrayan.spector.kernel.layout.InsularLayout;
import com.spectrayan.spector.kernel.layout.ProvenanceLayout;
import com.spectrayan.spector.kernel.layout.StrengthLayout;
import com.spectrayan.spector.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.kernel.layout.TemporalLayout;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.kernel.layout.WalLayout;
import com.spectrayan.spector.kernel.shape.AppendMemory;
import com.spectrayan.spector.kernel.shape.ChainMemory;
import com.spectrayan.spector.kernel.shape.GraphMemory;
import com.spectrayan.spector.kernel.shape.HashTableMemory;
import com.spectrayan.spector.kernel.shape.RecordMemory;
import com.spectrayan.spector.kernel.shape.RegistryMemory;
import com.spectrayan.spector.kernel.store.EntityDirectoryMemory;

import java.nio.file.Path;

/**
 * Kernel entry point for a single cognitive namespace (R16.2, R16.3).
 *
 * <p>Exposes typed shape interfaces for all runtime and partition bundle regions
 * without leaking any off-heap memory segments or arena scopes.</p>
 */
public interface NamespaceKernel extends AutoCloseable {

    String namespaceId();

    Path directory();

    // ── Dispatches on MemoryType and resolves physical locations ──
    EngramMemory engramMemory();

    default HeaderCursor cursor(MemoryType tier) {
        return engramMemory().cursor(tier);
    }

    // ── Single-region memories returned as their shape interfaces ──
    AppendMemory<TextBlobLayout> textMemory();

    RecordMemory<IndexEntryLayout> indexMemory();

    AppendMemory<WalLayout> walMemory();

    RecordMemory<StrengthLayout> strengthMemory();

    // ── Graph family: five shapes, no facade (R16.5) ──
    GraphMemory<HebbianLayout> hebbianMemory();

    GraphMemory<HyperEntityLayout> hyperGraphMemory();

    ChainMemory<TemporalLayout> temporalChainMemory();

    AppendMemory<TemporalFactLayout> temporalFactsMemory();

    HashTableMemory<CoActivationLayout> coActivationMemory();

    RegistryMemory entityTypeMemory();

    RegistryMemory relationTypeMemory();

    EntityDirectoryMemory entityDirectoryMemory();

    // ── Sidecar regions ──
    RecordMemory<InsularLayout> insulaMemory();

    RecordMemory<ContinuityLayout> continuityMemory();

    RecordMemory<ProvenanceLayout> provenanceMemory();

    // ── Operations ──
    com.spectrayan.spector.kernel.scratch.ScratchMemory scratch();

    com.spectrayan.spector.kernel.scan.ScanService scan();

    boolean hasActiveLeases();

    void flush();

    @Override
    void close();
}
