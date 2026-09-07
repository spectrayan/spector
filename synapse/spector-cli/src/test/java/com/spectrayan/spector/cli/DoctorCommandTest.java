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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorCommandTest {

    @Test
    void doctorCommand_textOutput_containsDiagnostics(@TempDir Path tempDir) {
        DoctorCommand cmd = new DoctorCommand();
        CommandLine cli = new CommandLine(cmd);
        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exitCode = cli.execute("--data-dir=" + tempDir.toAbsolutePath());

        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains("Spector System Diagnostics (Doctor)");
        assertThat(output).contains("Java Runtime");
        assertThat(output).contains("SIMD Vector API");
        assertThat(output).contains("Storage Permissions");
        assertThat(output).contains("In-Process ONNX");
        assertThat(output).contains("Synapse Daemon");
        assertThat(output).contains("AI Agent Integration Configuration");
    }

    @Test
    void doctorCommand_jsonOutput_containsAllSections(@TempDir Path tempDir) {
        CommandLine.IFactory factory = new CommandLine.IFactory() {
            @Override
            public <K> K create(Class<K> cls) throws Exception {
                if (cls == McpCommand.class) {
                    return cls.cast(new McpCommand(org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)));
                }
                if (cls == RememberCommand.class) {
                    return cls.cast(new RememberCommand(org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class), org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)));
                }
                return CommandLine.defaultFactory().create(cls);
            }
        };
        CommandLine cli = new CommandLine(new SpectorCtl(), factory);
        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exitCode = cli.execute("--json", "doctor", "--data-dir=" + tempDir.toAbsolutePath());

        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains("\"java\"");
        assertThat(output).contains("\"simd\"");
        assertThat(output).contains("\"storage\"");
        assertThat(output).contains("\"onnx\"");
        assertThat(output).contains("\"synapse\"");
    }

    @Test
    void doctorCommand_helpFlag() {
        DoctorCommand cmd = new DoctorCommand();
        CommandLine cli = new CommandLine(cmd);
        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exitCode = cli.execute("--help");

        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains("Diagnose the local environment");
        assertThat(output).contains("--data-dir");
    }
}
