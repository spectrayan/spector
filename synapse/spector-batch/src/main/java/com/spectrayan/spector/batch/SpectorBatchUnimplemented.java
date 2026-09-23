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
package com.spectrayan.spector.batch;

/**
 * Factory for the refusal thrown by every export/import step that is not yet implemented.
 *
 * <p>Until issue #981, the steps referenced here wrote hardcoded literals (two sample memory rows,
 * four literal vector bytes, one sample graph edge) and the import counterparts were
 * {@code log.info} no-ops. An operator following the documented migration path received an
 * {@code .smb} bundle containing none of their data, with {@code "verified": true} in its manifest,
 * and imported it to a log line claiming success.</p>
 *
 * <p>Those steps now refuse. Shipping nothing is safer than shipping a pipeline that reports
 * success it did not achieve: a missing feature is visible at the moment of use, whereas a false
 * success is invisible until the data is needed — which, for a system of record, is exactly the
 * moment when nothing can be done about it.</p>
 *
 * <p>The surrounding scaffolding is deliberately retained rather than deleted — the Spring Batch job
 * structure, {@code @StepScope} parameter binding, staging-directory handling and
 * {@link SpectorBundleCodec} (including its zip-slip guard) are sound and are reused by the real
 * implementation.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/981">spectrayan/spector#981</a>
 */
final class SpectorBatchUnimplemented {

    /** Spec that owns the real implementation of the export/import pipeline. */
    static final String OWNING_SPEC = "spectrayan/.kiro/specs/memory-portability";

    private SpectorBatchUnimplemented() {
        // Utility class
    }

    /**
     * Builds the refusal for an unimplemented export or import step.
     *
     * @param step   the step that was invoked, e.g. {@code "exportMemoryNodes"}
     * @param detail what the step used to do instead of real work
     * @return the exception to throw
     */
    static UnsupportedOperationException step(String step, String detail) {
        return new UnsupportedOperationException(
                "Spector batch step '" + step + "' is not implemented. " + detail
                        + " Producing or consuming an .smb bundle is disabled until the real pipeline "
                        + "ships; see " + OWNING_SPEC + " and spectrayan/spector#981. "
                        + "Do not use this job for migration or backup.");
    }
}
