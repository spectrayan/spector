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
package com.spectrayan.spector.cli;

import com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties;
import com.spectrayan.spector.synapse.dr.DisasterRecoveryExporter;
import com.spectrayan.spector.synapse.dr.DisasterRecoveryRestorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("spectorctl dr Command Specification (ADR-0034 §11.2, Req R4.1, G30)")
class DisasterRecoveryCommandTest {

    @Test
    @DisplayName("dr promote without --force fails and demands explicit acknowledgment (Req R4.1, R4.8)")
    void testPromoteWithoutForceFails() {
        DisasterRecoveryCommand cmdObj = new DisasterRecoveryCommand();
        CommandLine cmd = new CommandLine(cmdObj);
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("promote", "--cell", "cell-standby-02", "--reason", "Primary datacenter flood");

        assertThat(exitCode).isEqualTo(0); // picocli returns 0 from run() unless exception
        assertThat(err.toString()).contains("ERROR: Cell promotion requires '--force'");
    }

    @Test
    @DisplayName("dr promote with --force records auditable human promotion event and persists to file (Req R4.1, V3, G30)")
    void testPromoteWithForceSucceedsAndAudits(@TempDir Path tempDir) throws IOException {
        Path auditFile = tempDir.resolve("dr-promotion-audit.log");

        DisasterRecoveryCommand cmdObj = new DisasterRecoveryCommand();
        CommandLine cmd = new CommandLine(cmdObj);
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute(
                "promote",
                "--cell", "cell-standby-02",
                "--reason", "Cloud region outage",
                "--force",
                "--audit-file", auditFile.toString()
        );

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output)
                .contains("CELL PROMOTION AUDIT RECORD")
                .contains("cell-standby-02")
                .contains("Cloud region outage")
                .contains("ACTIVE_PRIMARY")
                .contains("Reverse path requires a structured data migration")
                .contains(auditFile.toAbsolutePath().toString());

        // G30: Verify audit record is persisted to disk
        assertThat(Files.exists(auditFile)).isTrue();
        String auditContent = Files.readString(auditFile);
        assertThat(auditContent)
                .contains("CELL_PROMOTION")
                .contains("cell-standby-02")
                .contains("Cloud region outage");
    }

    @Test
    @DisplayName("dr status in unconfigured environment prints NOT_CONFIGURED and UNKNOWN (G30)")
    void testDrStatusUnconfigured() {
        DisasterRecoveryCommand cmdObj = new DisasterRecoveryCommand();
        CommandLine cmd = new CommandLine(cmdObj);
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("status");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output)
                .contains("Disaster Recovery Posture: NOT_CONFIGURED")
                .contains("Standby Posture:          NOT_CONFIGURED")
                .contains("Export Interval:          NOT_CONFIGURED")
                .contains("Measured RPO:             UNKNOWN");
    }

    @Test
    @DisplayName("dr status with live exporter reporting measured RPO shows READY and measured interval (G30)")
    void testDrStatusWithMeasuredRpo() {
        DisasterRecoveryProperties props = new DisasterRecoveryProperties();
        props.setExportEnabled(true);
        props.setExportIntervalSeconds(900);
        props.setStandbyMode("WARM_SYNC");

        DisasterRecoveryExporter exporter = mock(DisasterRecoveryExporter.class);
        when(exporter.getMeasuredP99Rpo()).thenReturn(OptionalLong.of(42L));

        DisasterRecoveryCommand.StatusSubcommand statusCmd = new DisasterRecoveryCommand.StatusSubcommand(props, exporter);
        CommandLine cmd = new CommandLine(statusCmd);
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute();
        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output)
                .contains("Disaster Recovery Posture: READY")
                .contains("Standby Posture:          WARM_SYNC")
                .contains("Export Interval:          900s")
                .contains("Measured RPO:             42s");
    }

    @Test
    @DisplayName("dr restore without restorer reports error (G30)")
    void testRestoreWithoutRestorerFails() {
        DisasterRecoveryCommand.RestoreNamespaceSubcommand restoreCmd = new DisasterRecoveryCommand.RestoreNamespaceSubcommand(null);
        CommandLine cmd = new CommandLine(restoreCmd);
        StringWriter err = new StringWriter();
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("--namespace", "018f9b8c000070008000000000000042");
        assertThat(exitCode).isEqualTo(0);
        assertThat(err.toString()).contains("ERROR: Disaster recovery restorer is not configured");
    }

    @Test
    @DisplayName("dr restore with restorer executes and prints restored HWM (G30)")
    void testRestoreWithRestorerSucceeds() {
        DisasterRecoveryRestorer restorer = mock(DisasterRecoveryRestorer.class);
        when(restorer.discoverSelectableEpochs("untenanted", "018f9b8c000070008000000000000042"))
                .thenReturn(List.of(1L, 2L));

        DisasterRecoveryRestorer.RestoreResult mockResult = new DisasterRecoveryRestorer.RestoreResult(
                "018f9b8c000070008000000000000042",
                "untenanted",
                2L,
                450L,
                Path.of("/tmp/restore/018f9b8c000070008000000000000042"),
                5,
                120L,
                true,
                null
        );

        when(restorer.restoreNamespace(eq("untenanted"), eq("018f9b8c000070008000000000000042"), eq(2L), any(), isNull()))
                .thenReturn(mockResult);

        DisasterRecoveryCommand.RestoreNamespaceSubcommand restoreCmd = new DisasterRecoveryCommand.RestoreNamespaceSubcommand(restorer);
        CommandLine cmd = new CommandLine(restoreCmd);
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("--namespace", "018f9b8c000070008000000000000042");
        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output)
                .contains("DISASTER RECOVERY RESTORE COMPLETE")
                .contains("018f9b8c000070008000000000000042")
                .contains("Epoch:        2")
                .contains("Restored HWM: 450")
                .contains("Artifacts:    5 verified");
    }
}
