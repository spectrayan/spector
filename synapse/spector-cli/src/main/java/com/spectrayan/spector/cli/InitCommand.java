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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command initializing local Spector directories and default configuration (spector.yml).
 */
@Component
@Command(
        name = "init",
        description = "Initialize local Spector configuration and storage directories.",
        mixinStandardHelpOptions = true
)
public class InitCommand extends BaseCommand {

    @Option(names = {"--config-dir"}, description = "Directory for configuration (default: ~/.spector).")
    private String configDir;

    @Option(names = {"--data-dir"}, description = "Directory for memory and vector storage (default: ~/.spector/data).")
    private String dataDir;

    @Option(names = {"--force"}, description = "Overwrite existing configuration file if present.")
    private boolean force;

    private static final String DEFAULT_CONFIG_CONTENT = """
            # ─────────────────────────────────────────────────────────────
            # Spector Local Configuration (~/.spector/spector.yml)
            # ─────────────────────────────────────────────────────────────
            spector:
              port: 7070
              data-dir: ~/.spector/data

              memory:
                enabled: true
                dimensions: 384
                persistence-mode: DISK
                persistence-path: ~/.spector/data/cognitive
                capacity: 10000

              provider:
                embedding:
                  # Default: in-process zero-config ONNX model (offline, no external servers)
                  type: onnx
                  model: all-MiniLM-L6-v2
                  dimensions: 384

                  # To switch to local Ollama, uncomment below:
                  # type: ollama
                  # model: nomic-embed-text
                  # base-url: http://localhost:11434
                  # dimensions: 768
            """;

    @Override
    public void run() {
        String homeDir = System.getProperty("user.home");
        String resolvedConfigDir = configDir != null && !configDir.isBlank()
                ? configDir
                : homeDir + File.separator + ".spector";
        String resolvedDataDir = dataDir != null && !dataDir.isBlank()
                ? dataDir
                : resolvedConfigDir + File.separator + "data";

        Path configDirPath = Paths.get(resolvedConfigDir);
        Path dataDirPath = Paths.get(resolvedDataDir);
        Path configFile = configDirPath.resolve("spector.yml");

        boolean createdConfig = false;
        boolean overwrittenConfig = false;
        boolean configExists = Files.exists(configFile);

        try {
            Files.createDirectories(configDirPath);
            Files.createDirectories(dataDirPath);

            if (!configExists || force) {
                Files.writeString(configFile, DEFAULT_CONFIG_CONTENT);
                if (configExists) {
                    overwrittenConfig = true;
                } else {
                    createdConfig = true;
                }
            }
        } catch (Exception e) {
            err().println("Error initializing Spector environment: " + e.getMessage());
            return;
        }

        if (isJson()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("configDir", resolvedConfigDir);
            result.put("dataDir", resolvedDataDir);
            result.put("configFile", configFile.toString());
            result.put("created", createdConfig);
            result.put("overwritten", overwrittenConfig);
            result.put("status", "SUCCESS");
            OutputFormatter.printJson(out(), result);
            return;
        }

        out().println();
        out().println("================================================================================");
        out().println("                     Spector Environment Initialized                            ");
        out().println("================================================================================");
        out().println();
        out().println("  [OK] Config Directory : " + configDirPath.toAbsolutePath());
        out().println("  [OK] Data Directory   : " + dataDirPath.toAbsolutePath());

        if (createdConfig) {
            out().println("  [OK] Config File       : " + configFile.toAbsolutePath() + " (created)");
        } else if (overwrittenConfig) {
            out().println("  [OK] Config File       : " + configFile.toAbsolutePath() + " (overwritten via --force)");
        } else {
            out().println("  [INFO] Config File     : " + configFile.toAbsolutePath() + " (already exists; use --force to overwrite)");
        }

        out().println();
        out().println("Next Steps:");
        out().println("  1. Run diagnostics:");
        out().println("     spectorctl doctor");
        out().println();
        out().println("  2. Start stdio MCP server for Claude Desktop / Cursor:");
        out().println("     spectorctl mcp");
        out().println();
        out().println("  3. Start the Synapse daemon server:");
        out().println("     java -jar spector-synapse.jar");
        out().println();
    }
}
