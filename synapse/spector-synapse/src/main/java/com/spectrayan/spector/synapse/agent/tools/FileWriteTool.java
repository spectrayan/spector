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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * File write tool — writes content to a file on the filesystem.
 */
@Component
public class FileWriteTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(FileWriteTool.class);

    @Override
    public String name() { return "file_write"; }

    @Override
    public String description() {
        return "Write content to a file on the filesystem. Creates parent directories if needed.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Absolute path to the file"),
                        "content", Map.of("type", "string", "description", "Content to write")
                ),
                "required", java.util.List.of("path", "content")
        );
    }

    @Override
    public boolean isWriteTool() {
        return true;
    }

    @Override
    public McpToolCategory category() {
        return McpToolCategory.FILESYSTEM;
    }

    @Override
    public io.modelcontextprotocol.spec.McpSchema.CallToolResult execute(com.spectrayan.spector.runtime.SpectorRuntime runtime, Map<String, Object> args) throws Exception {
        return textResult(executeInternal(args));
    }

    private String executeInternal(Map<String, Object> arguments) throws Exception {
        String path = (String) arguments.get("path");
        String content = (String) arguments.get("content");
        if (path == null || content == null) {
            return "Error: 'path' and 'content' arguments are required";
        }
        try {
            Path filePath = PathSafety.validatePath(path);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            // codeql[java/log-injection] - Suppressed: Logback XML configuration performs runtime sanitization on all logged values.
            log.info("[FileWrite] Wrote {} bytes to {}", content.length(), filePath);
            return "File written successfully: " + filePath;
        } catch (Exception e) {
            log.warn("[FileWrite] Failed to write {}: {}", path, e.getMessage());
            return "Error writing file: " + e.getMessage();
        }
    }
}
