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

import com.spectrayan.spector.kernel.migration.TextAppendCodec;
import com.spectrayan.spector.kernel.migration.TypeRegistryCodec;
import com.spectrayan.spector.kernel.migration.HebbianGraphCodec;
import com.spectrayan.spector.kernel.migration.IndexRecordCodec;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.layout.RegionLayout;
import com.spectrayan.spector.kernel.migration.TemporalChainCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Static entry point for ensuring memory files are converted to current SMKM schema.
 */
public final class Codecs {

    private static final CodecRegistry DEFAULT_REGISTRY = CodecRegistry.builder()
            .register(new TemporalChainCodec())
            .register(new TextAppendCodec())
            .register(new TypeRegistryCodec())
            .register(new IndexRecordCodec())
            // #435: HebbianGraphCodec is the single migration authority for the Hebbian
            // association graph. It migrates BOTH the legacy HGPH container and the interim
            // HCSR container (#432) to the kernel SMKM CSR format, which
            // HebbianGraphMemory.load() now reads natively — resolving the #432 data-loss
            // regression where the codec produced a format the loader could not read.
            .register(new HebbianGraphCodec())
            // #435: HyperEntityGraphCodec was DEREGISTERED. Like Entity, the hyper-entity graph
            // now uses in-class migration (HyperEntityGraphMemory.load) as the SOLE authority
            // (CEO decision). The old codec had no migration steps, so registering it here only
            // risked re-introducing the two-authority #432 trap.
            .build();

    private Codecs() {}

    public static CodecRegistry defaultRegistry() {
        return DEFAULT_REGISTRY;
    }

    public static MigrationResult ensureCurrent(CodecRegistry registry, MemoryId id,
                                                RegionLayout layout, Path filePath,
                                                Object enc, Map<String, Path> sidecars)
            throws IOException {
        if (filePath == null) {
            return MigrationResult.freshFile(FormatId.smkm(layout.schemaVersion()));
        }

        CodecRegistry reg = (registry != null) ? registry : DEFAULT_REGISTRY;
        Optional<FormatCodec<?>> codecOpt = reg.byLayoutId(layout.layoutId());
        if (codecOpt.isEmpty()) {
            return MigrationResult.freshFile(FormatId.smkm(layout.schemaVersion()));
        }

        MigrationContext ctx = new MigrationContext(
                filePath, id, layout, enc, sidecars, true, false
        );

        return codecOpt.get().ensureCurrent(ctx);
    }
}
