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
package com.spectrayan.spector.kernel.scan;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;

import java.lang.foreign.MemorySegment;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Emits tier scan work inside the kernel (R7.6).
 */
public interface ScanEmitter {

    /** Emits a full-record slab scan of the given store slice. */
    void emitSlabScan(Supplier<MemorySegment> segment, IntSupplier visibleCount,
                      FixedEngramLayout layout, MemoryType type,
                      long baseOffset, int partitionSeq);
}
