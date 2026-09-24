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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.tasklet.Tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards that no export/import step silently fabricates or discards data.
 *
 * <p>Before issue #981 these steps wrote hardcoded literals (two sample memory rows, four literal
 * vector bytes, one invented graph edge, a literal {@code AES-256-GCM} claim) and the import
 * counterparts were {@code log.info} no-ops. The job reported success throughout, and
 * {@code validateExportStep} stamped {@code "verified": true} onto a manifest describing its own
 * fixtures.</p>
 *
 * <p>Each assertion below checks the exception <b>type and message content</b> rather than merely that
 * something was thrown. A test asserting only "throws" would pass against a bare
 * {@code throw new RuntimeException()} and would not detect a regression back to a step that writes
 * fixtures and returns {@code FINISHED}.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/981">spectrayan/spector#981</a>
 */
@DisplayName("Export/import steps refuse rather than fabricate (#981)")
class SpectorBatchUnimplementedStepsTest {

    /** The lambdas refuse before touching either collaborator, so nulls are safe here. */
    private final SpectorExportJobConfig exportConfig = new SpectorExportJobConfig(null, null);
    private final SpectorImportJobConfig importConfig = new SpectorImportJobConfig(null, null);

    private static void assertRefuses(Tasklet tasklet, String stepName) {
        assertThatThrownBy(() -> tasklet.execute(null, null))
                .as("step '%s' must refuse rather than fabricate", stepName)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining(stepName)
                .hasMessageContaining(SpectorBatchUnimplemented.OWNING_SPEC)
                .hasMessageContaining("#981")
                .hasMessageContaining("Do not use this job for migration or backup");
    }

    @Nested
    @DisplayName("Export")
    class ExportSteps {

        @Test
        @DisplayName("export steps refuse when no SpectorMemory or resolver is configured")
        void exportStepsRefuseWithoutMemory() {
            assertThatThrownBy(() -> exportConfig.exportMemoryNodesTasklet(null, "/tmp/b.smb").execute(null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no SpectorMemory or SpectorMemoryResolver available");

            assertThatThrownBy(() -> exportConfig.exportGraphTasklet(null, "/tmp/b.smb").execute(null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no SpectorMemory or SpectorMemoryResolver available");
        }
    }

    @Nested
    @DisplayName("Import")
    class ImportSteps {

        @Test
        @DisplayName("import steps refuse when no SpectorMemory or resolver is configured")
        void allImportWriteStepsRefuse() {
            assertThatThrownBy(() -> importConfig.importMemoryNodesTasklet("/tmp/b.smb", "ns").execute(null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no SpectorMemory or SpectorMemoryResolver available");

            assertThatThrownBy(() -> importConfig.importGraphTasklet("/tmp/b.smb", "ns").execute(null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no SpectorMemory or SpectorMemoryResolver available");

            assertThatThrownBy(() -> importConfig.rebuildVectorIndexTasklet("/tmp/b.smb", "ns").execute(null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no SpectorMemory or SpectorMemoryResolver available");
        }

        @Test
        @DisplayName("refusal happens before anything is written to the target namespace")
        void refusesBeforeWriting() {
            assertThatThrownBy(() -> importConfig.importMemoryNodesTasklet("/tmp/b.smb", "ns").execute(null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no SpectorMemory or SpectorMemoryResolver available");
        }
    }

    @Test
    @DisplayName("the refusal names the spec that owns the real implementation")
    void refusalPointsAtTheOwningSpec() {
        assertThat(SpectorBatchUnimplemented.OWNING_SPEC)
                .as("an operator hitting this needs a pointer, not a puzzle")
                .isEqualTo("spectrayan/.kiro/specs/memory-portability");
    }
}
