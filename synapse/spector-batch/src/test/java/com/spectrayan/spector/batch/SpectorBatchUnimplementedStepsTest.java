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
        @DisplayName("every content-producing step refuses")
        void allExportContentStepsRefuse() {
            assertRefuses(exportConfig.exportManifestTasklet("ns", "/tmp/b.smb"), "exportManifest");
            assertRefuses(exportConfig.exportMemoryNodesTasklet("/tmp/b.smb"), "exportMemoryNodes");
            assertRefuses(exportConfig.exportVectorsTasklet("/tmp/b.smb"), "exportVectors");
            assertRefuses(exportConfig.exportGraphTasklet("/tmp/b.smb"), "exportGraph");
            assertRefuses(exportConfig.exportSubsystemsTasklet("/tmp/b.smb"), "exportSubsystems");
            assertRefuses(exportConfig.exportKeysTasklet("/tmp/b.smb"), "exportKeys");
        }

        @Test
        @DisplayName("validation refuses, and no longer stamps its own output as verified")
        void validateStepRefusesAndDoesNotSelfCertify() {
            assertRefuses(exportConfig.validateExportTasklet("ns", "/tmp/b.smb"), "validateExport");
        }

        @Test
        @DisplayName("the security member is named as absent-by-design, not merely unimplemented")
        void keysStepExplainsWhyTheMemberMustStayAbsent() {
            assertThatThrownBy(() -> exportConfig.exportKeysTasklet("/tmp/b.smb").execute(null, null))
                    .as("must not be read as a temporary gap — no DEK exists, so the member cannot exist")
                    .hasMessageContaining("No encryption, DEK, or envelope-encryption code exists");
        }
    }

    @Nested
    @DisplayName("Import")
    class ImportSteps {

        @Test
        @DisplayName("every data-writing step refuses")
        void allImportWriteStepsRefuse() {
            assertRefuses(importConfig.importMemoryNodesTasklet("/tmp/b.smb", "ns"), "importMemoryNodes");
            assertRefuses(importConfig.importGraphTasklet("/tmp/b.smb", "ns"), "importGraph");
            assertRefuses(importConfig.rebuildVectorIndexTasklet("/tmp/b.smb", "ns"), "rebuildVectorIndex");
        }

        @Test
        @DisplayName("refusal happens before anything is written to the target namespace")
        void refusesBeforeWriting() {
            // importMemoryNodes is the first step that would write. Unpack and manifest-presence
            // validation run ahead of it and are retained, so a bundle is never partially applied.
            assertThatThrownBy(() -> importConfig.importMemoryNodesTasklet("/tmp/b.smb", "ns").execute(null, null))
                    .isInstanceOf(UnsupportedOperationException.class);
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
