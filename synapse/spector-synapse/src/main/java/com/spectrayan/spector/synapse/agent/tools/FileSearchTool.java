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

import com.spectrayan.spector.synapse.agent.AgentTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Searches file contents by pattern (regex or literal) — recursive directory search.
 *
 * <p>Skips binary files, respects max file size, and excludes common
 * non-source directories (.git, node_modules, target).</p>
 */
@Component
public class FileSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FileSearchTool.class);
    private static final int MAX_RESULTS = 100;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> SKIP_DIRS = Set.of(".git", "node_modules", "target", ".idea", "build");

    @Override public String name() { return "file_search"; }

    @Override public String description() {
        return "Search file contents by pattern. Returns matching file paths with line numbers and content snippets.";
    }

    @Override public ToolCategory category() { return ToolCategory.FILESYSTEM; }
    @Override public boolean isWriteTool() { return false; }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Search pattern (regex or literal string)"),
                        "path", Map.of("type", "string", "description", "Directory to search (absolute path)"),
                        "isRegex", Map.of("type", "boolean", "description", "Treat query as regex (default: false)"),
                        "filePattern", Map.of("type", "string", "description", "File name glob filter (e.g., '*.java')")
                ),
                "required", List.of("query", "path")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        var query = (String) args.get("query");
        if (query == null || query.isBlank()) return "Error: Missing required argument: query";

        var pathArg = args.get("path");
        if (pathArg == null) return "Error: Missing required argument: path";

        Path dirPath;
        try {
            dirPath = PathSafety.validatePath(pathArg.toString());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
        if (!Files.isDirectory(dirPath)) return "Error: Not a directory: " + pathArg;

        boolean isRegex = Boolean.parseBoolean(String.valueOf(args.getOrDefault("isRegex", "false")));
        String filePattern = args.get("filePattern") != null ? args.get("filePattern").toString() : null;

        Pattern pattern;
        try {
            // codeql[java/regular-expression-injection] - Suppressed: FileSearchTool by design compiles user-provided patterns for searching.
            pattern = isRegex
                    ? Pattern.compile(query, Pattern.MULTILINE)
                    : Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        } catch (java.util.regex.PatternSyntaxException e) {
            return "Error: Invalid regular expression pattern: " + e.getMessage();
        }

        var matches = new ArrayList<String>();

        try {
            Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matches.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                    if (attrs.size() > MAX_FILE_SIZE) return FileVisitResult.CONTINUE;
                    if (filePattern != null && !file.getFileName().toString().matches(globToRegex(filePattern))) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size() && matches.size() < MAX_RESULTS; i++) {
                            if (pattern.matcher(lines.get(i)).find()) {
                                String trimmed = lines.get(i).trim();
                                matches.add("%s:%d: %s".formatted(
                                        dirPath.relativize(file), i + 1,
                                        trimmed.substring(0, Math.min(trimmed.length(), 120))
                                ));
                            }
                        }
                    } catch (IOException _) {
                        // Skip binary or unreadable files
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (SKIP_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }

        if (matches.isEmpty()) return "No matches found for: " + query;
        log.debug("[FileSearchTool] Found {} matches for '{}'", matches.size(), query);
        return "Found " + matches.size() + " match(es):\n" + String.join("\n", matches);
    }

    private static String globToRegex(String glob) {
        return glob.replace(".", "\\.").replace("*", ".*").replace("?", ".");
    }
}
