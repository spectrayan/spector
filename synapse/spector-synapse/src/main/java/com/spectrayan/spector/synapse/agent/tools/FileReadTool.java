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
 * File read tool — reads the contents of a file from the filesystem.
 */
@Component
public class FileReadTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(FileReadTool.class);

    @Override
    public String name() { return "file_read"; }

    @Override
    public String description() {
        return "Read the contents of a file from the filesystem. Requires a 'path' argument.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Absolute path to the file to read")
                ),
                "required", java.util.List.of("path")
        );
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
        if (path == null || path.isBlank()) {
            return "Error: 'path' argument is required";
        }
        try {
            Path validatedPath = PathSafety.validatePath(path);
            String content = Files.readString(validatedPath);
            // codeql[java/log-injection] - Suppressed: Logback XML configuration performs runtime sanitization on all logged values.
            log.debug("[FileRead] Read {} bytes from {}", content.length(), validatedPath);
            return content;
        } catch (Exception e) {
            log.warn("[FileRead] Failed to read {}: {}", path, e.getMessage());
            return "Error reading file: " + e.getMessage();
        }
    }
}
