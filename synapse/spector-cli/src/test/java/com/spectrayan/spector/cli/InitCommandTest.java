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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InitCommandTest {

    @Test
    void initCommand_createsConfigAndDataDirectory(@TempDir Path tempDir) {
        Path configDir = tempDir.resolve(".spector");
        Path dataDir = configDir.resolve("data");

        InitCommand cmd = new InitCommand();
        CommandLine cli = new CommandLine(cmd);
        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exitCode = cli.execute(
                "--config-dir=" + configDir.toAbsolutePath(),
                "--data-dir=" + dataDir.toAbsolutePath()
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.exists(configDir)).isTrue();
        assertThat(Files.exists(dataDir)).isTrue();
        assertThat(Files.exists(configDir.resolve("spector.yml"))).isTrue();

        String output = sw.toString();
        assertThat(output).contains("Spector Environment Initialized");
        assertThat(output).contains("Config File");
        assertThat(output).contains("Next Steps:");
    }

    @Test
    void initCommand_doesNotOverwriteExistingWithoutForce(@TempDir Path tempDir) throws Exception {
        Path configDir = tempDir.resolve(".spector");
        Path dataDir = configDir.resolve("data");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("spector.yml");
        Files.writeString(configFile, "custom: config");

        InitCommand cmd = new InitCommand();
        CommandLine cli = new CommandLine(cmd);
        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exitCode = cli.execute(
                "--config-dir=" + configDir.toAbsolutePath(),
                "--data-dir=" + dataDir.toAbsolutePath()
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.readString(configFile)).isEqualTo("custom: config");
        assertThat(sw.toString()).contains("already exists; use --force to overwrite");
    }

    @Test
    void initCommand_overwritesWithForce(@TempDir Path tempDir) throws Exception {
        Path configDir = tempDir.resolve(".spector");
        Path dataDir = configDir.resolve("data");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("spector.yml");
        Files.writeString(configFile, "custom: config");

        InitCommand cmd = new InitCommand();
        CommandLine cli = new CommandLine(cmd);
        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exitCode = cli.execute(
                "--config-dir=" + configDir.toAbsolutePath(),
                "--data-dir=" + dataDir.toAbsolutePath(),
                "--force"
        );

        assertThat(exitCode).isEqualTo(0);
        assertThat(Files.readString(configFile)).contains("all-MiniLM-L6-v2");
        assertThat(sw.toString()).contains("overwritten via --force");
    }

    @Test
    void initCommand_jsonOutput(@TempDir Path tempDir) {
        Path configDir = tempDir.resolve(".spector");
        Path dataDir = configDir.resolve("data");

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

        int exitCode = cli.execute(
                "--json", "init",
                "--config-dir=" + configDir.toAbsolutePath(),
                "--data-dir=" + dataDir.toAbsolutePath()
        );

        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains("\"status\"");
        assertThat(output).contains("\"SUCCESS\"");
        assertThat(output).contains("\"configFile\"");
    }
}
