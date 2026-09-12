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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("spectorctl dr Command Specification (ADR-0034 §11.2, Req R4.1)")
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
    @DisplayName("dr promote with --force records auditable human promotion event (Req R4.1, V3)")
    void testPromoteWithForceSucceedsAndAudits() {
        DisasterRecoveryCommand cmdObj = new DisasterRecoveryCommand();
        CommandLine cmd = new CommandLine(cmdObj);
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("promote", "--cell", "cell-standby-02", "--reason", "Cloud region outage", "--force");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output)
                .contains("CELL PROMOTION AUDIT RECORD")
                .contains("cell-standby-02")
                .contains("Cloud region outage")
                .contains("ACTIVE_PRIMARY")
                .contains("Reverse path requires a structured data migration");
    }

    @Test
    @DisplayName("dr status displays disaster recovery readiness posture")
    void testDrStatus() {
        DisasterRecoveryCommand cmdObj = new DisasterRecoveryCommand();
        CommandLine cmd = new CommandLine(cmdObj);
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("status");

        assertThat(exitCode).isEqualTo(0);
        String output = out.toString();
        assertThat(output)
                .contains("Disaster Recovery Posture: READY")
                .contains("COLD_AT_ZERO")
                .contains("15m (RPO SLA)");
    }
}
