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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists directory contents with metadata — supports recursive listing
 * and glob-pattern filtering.
 */
@Component
public class DirectoryListTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(DirectoryListTool.class);
    private static final int MAX_RESULTS = 500;

    @Override public String name() { return "directory_list"; }

    @Override public String description() {
        return "List directory contents with metadata. Returns file name, size, last modified, and type for each entry.";
    }

    @Override public McpToolCategory category() { return McpToolCategory.FILESYSTEM; }
    @Override public boolean isWriteTool() { return false; }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "Absolute path to directory"),
                        "recursive", Map.of("type", "boolean", "description", "List descendants recursively (default: false)"),
                        "pattern", Map.of("type", "string", "description", "Glob pattern filter (e.g., '*.java', '**/*.yml')")
                ),
                "required", List.of("path")
        );
    }

    @Override
    public io.modelcontextprotocol.spec.McpSchema.CallToolResult execute(com.spectrayan.spector.runtime.SpectorRuntime runtime, Map<String, Object> args) throws Exception {
        return textResult(executeInternal(args));
    }

    private String executeInternal(Map<String, Object> args) throws Exception {
        var pathArg = args.get("path");
        if (pathArg == null) return "Error: Missing required argument: path";

        Path dir;
        try {
            dir = PathSafety.validatePath(pathArg.toString());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
        if (!Files.isDirectory(dir)) return "Error: Not a directory: " + pathArg;

        boolean recursive = Boolean.parseBoolean(String.valueOf(args.getOrDefault("recursive", "false")));
        String pattern = args.get("pattern") != null ? args.get("pattern").toString() : null;
        PathMatcher matcher = pattern != null
                ? FileSystems.getDefault().getPathMatcher("glob:" + pattern)
                : null;

        var entries = new ArrayList<String>();

        try {
            if (recursive) {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (entries.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                        if (matcher == null || matcher.matches(file.getFileName())) {
                            entries.add(formatEntry(dir, file, attrs));
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                        if (!d.equals(dir)) entries.add(formatDir(dir, d, attrs));
                        return entries.size() >= MAX_RESULTS ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }
                });
            } else {
                try (var stream = Files.list(dir)) {
                    stream.limit(MAX_RESULTS).forEach(p -> {
                        try {
                            var attrs = Files.readAttributes(p, BasicFileAttributes.class);
                            if (attrs.isDirectory()) {
                                entries.add(formatDir(dir, p, attrs));
                            } else if (matcher == null || matcher.matches(p.getFileName())) {
                                entries.add(formatEntry(dir, p, attrs));
                            }
                        } catch (IOException e) {
                            entries.add(dir.relativize(p) + "  [error reading]");
                        }
                    });
                }
            }
        } catch (IOException e) {
            return "Error listing directory: " + e.getMessage();
        }

        if (entries.isEmpty()) return "Directory is empty" + (pattern != null ? " (filter: " + pattern + ")" : "");
        log.debug("[DirectoryListTool] Listed {} entries from {}", entries.size(), dir);
        return String.join("\n", entries);
    }

    private static String formatEntry(Path base, Path file, BasicFileAttributes attrs) {
        return "%-50s %10d bytes  %s".formatted(
                base.relativize(file), attrs.size(),
                Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()));
    }

    private static String formatDir(Path base, Path dir, BasicFileAttributes attrs) {
        return "%-50s      [DIR]  %s".formatted(
                base.relativize(dir) + "/",
                Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()));
    }
}
