/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.tools;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.runtime.SpectorRuntime;
import io.modelcontextprotocol.spec.McpSchema;


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
public class ShellExecutionTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(ShellExecutionTool.class);
    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public String name() { return "shell_execute"; }

    @Override
    public String description() {
        return "Execute a shell command and return stdout output. Use with caution.";
    }

    @Override
    public Map<String, Object> inputSchema() {
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
    public McpToolCategory category() {
        return McpToolCategory.SYSTEM;
    }

    @Override
    public io.modelcontextprotocol.spec.McpSchema.CallToolResult execute(com.spectrayan.spector.runtime.SpectorRuntime runtime, Map<String, Object> args) throws Exception {
        return textResult(executeInternal(args));
    }

    private String executeInternal(Map<String, Object> arguments) throws Exception {
        String command = (String) arguments.get("command");
        String workDir = (String) arguments.get("workDir");

        if (command == null || command.isBlank()) {
            return "Error: 'command' argument is required";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            String shell;
            String flag;
            if (os.contains("win")) {
                String comspec = System.getenv("COMSPEC");
                shell = (comspec != null && !comspec.isBlank()) ? comspec : "C:\\Windows\\System32\\cmd.exe";
                flag = "/c";
            } else {
                shell = "/bin/sh";
                flag = "-c";
            }

            // codeql[java/command-line-injection] - Suppressed: This is a shell execution tool designed to run arbitrary agent commands, gated by client-side HITL approval.
            // codeql[java/uncontrolled-command-line] - Suppressed: This is a shell execution tool designed to run arbitrary agent commands, gated by client-side HITL approval.
            // codeql[java/relative-path-command] - Suppressed: Absolute shell executable resolved above.
            pb.command(shell, flag, command);

            if (workDir != null && !workDir.isBlank()) {
                // Ensure workDir is validated against path traversal if needed, but ProcessBuilder handles directory change
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
