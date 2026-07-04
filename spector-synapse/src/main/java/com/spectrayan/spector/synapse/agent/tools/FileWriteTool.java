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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * File write tool — writes content to a file on the filesystem.
 */
@Component
public class FileWriteTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FileWriteTool.class);

    @Override
    public String name() { return "file_write"; }

    @Override
    public String description() {
        return "Write content to a file on the filesystem. Creates parent directories if needed.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
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
    public String execute(Map<String, Object> arguments) {
        String path = (String) arguments.get("path");
        String content = (String) arguments.get("content");
        if (path == null || content == null) {
            return "Error: 'path' and 'content' arguments are required";
        }
        try {
            Path filePath = Path.of(path);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            log.info("[FileWrite] Wrote {} bytes to {}", content.length(), path);
            return "File written successfully: " + path;
        } catch (IOException e) {
            log.warn("[FileWrite] Failed to write {}: {}", path, e.getMessage());
            return "Error writing file: " + e.getMessage();
        }
    }
}
