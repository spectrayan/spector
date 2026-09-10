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
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.kernel.migration.FormatCodec;
import com.spectrayan.spector.kernel.migration.CodecStep;
import com.spectrayan.spector.kernel.layout.HebbianLayout;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Set;

/**
 * Codec binding for HebbianGraphMemory format.
 */
public final class HebbianGraphCodec implements FormatCodec<HebbianLayout> {

    private final HebbianLayout layout = new HebbianLayout();

    @Override
    public HebbianLayout layout() {
        return layout;
    }

    @Override
    public Set<Integer> legacyMagics() {
        return Set.of(HgphToCsrStep.FROM_MAGIC, HcsrToSmkmStep.FROM_MAGIC);
    }

    @Override
    public int versionOf(int magic, MemorySegment headerPrefix) {
        return 1;
    }

    @Override
    public List<CodecStep> steps() {
        // Two terminal hops to the SMKM CSR container: from the legacy HGPH format and
        // from the interim HCSR format (#432). Both are the single migration authority
        // for Hebbian (#435); load() understands the SMKM output they produce.
        return List.of(new HgphToCsrStep(), new HcsrToSmkmStep());
    }
}
