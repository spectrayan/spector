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
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.synapse.agent.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell execution tool — runs shell commands and returns output.
 *
 * <p><strong>Security note:</strong> This tool should be used with caution.
 * Commands are executed with the Synapse process's permissions.</p>
 */
@Component
public class ShellExecutionTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ShellExecutionTool.class);
    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public String name() { return "shell_execute"; }

    @Override
    public String description() {
        return "Execute a shell command and return stdout output. Use with caution.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of("type", "string", "description", "Shell command to execute"),
                        "workDir", Map.of("type", "string", "description", "Working directory (optional)")
                ),
                "required", java.util.List.of("command")
        );
    }

    @Override
    public boolean isWriteTool() {
        return true;
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.SYSTEM;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String command = (String) arguments.get("command");
        String workDir = (String) arguments.get("workDir");

        if (command == null || command.isBlank()) {
            return "Error: 'command' argument is required";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }

            if (workDir != null && !workDir.isBlank()) {
                pb.directory(new java.io.File(workDir));
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: Command timed out after " + TIMEOUT_SECONDS + " seconds";
            }

            int exitCode = process.exitValue();
            log.info("[Shell] '{}' → exit={}, {} bytes output", command, exitCode, output.length());
            return output.toString();
        } catch (Exception e) {
            log.warn("[Shell] Failed to execute '{}': {}", command, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
