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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.file.FileAccountCatalog;
import com.spectrayan.spector.synapse.migration.TenantNamespaceMigrator;
import com.spectrayan.spector.synapse.migration.TenantNamespaceMigrator.MigrationSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CLI command to migrate flat namespace directories to tenant-rooted layout (ADR-0033 D3=C, Task 4.5, Requirements R5.7).
 */
@Component
@Command(
        name = "migrate-namespaces",
        description = "Migrate flat namespaces to tenant-rooted layout.",
        mixinStandardHelpOptions = true
)
public class MigrateNamespacesCommand extends BaseCommand {

    @Option(names = {"--dry-run"}, description = "Report the migration plan without modifying the filesystem.")
    private boolean dryRun;

    @Option(names = {"--data-dir"}, description = "Base directory for memory persistence (default: ~/.spector/data).")
    private String dataDir;

    @Autowired(required = false)
    private AccountCatalog injectedCatalog;

    @Override
    public void run() {
        PrintWriter out = out();
        PrintWriter err = err();

        Path basePath;
        if (dataDir != null && !dataDir.isBlank()) {
            basePath = Paths.get(dataDir);
        } else {
            String userHome = System.getProperty("user.home", ".");
            basePath = Paths.get(userHome, ".spector", "data");
        }

        AccountCatalog catalog = injectedCatalog;
        if (catalog == null) {
            catalog = new FileAccountCatalog(basePath, new ObjectMapper());
        }

        out.println(String.format("Scanning persistence root for tenanted namespaces: %s (dry-run=%s)",
                basePath.toAbsolutePath(), dryRun));

        MigrationSummary summary;
        try {
            summary = TenantNamespaceMigrator.migrate(basePath, catalog, null, null, new ObjectMapper(), dryRun);
        } catch (Exception e) {
            err.println("Migration failed with error: " + e.getMessage());
            throw new RuntimeException("Migration failed", e);
        }

        if (isJson()) {
            Map<String, Object> jsonMap = new LinkedHashMap<>();
            jsonMap.put("basePath", basePath.toString());
            jsonMap.put("dryRun", dryRun);
            jsonMap.put("migrated", summary.migrated());
            jsonMap.put("skipped", summary.skipped());
            jsonMap.put("errors", summary.errors());
            OutputFormatter.printJson(out, jsonMap);
        } else {
            out.println("==================================================");
            out.println("Namespace Migration Summary");
            out.println("==================================================");
            out.printf("Mode:     %s\n", dryRun ? "DRY-RUN (no changes made)" : "LIVE");
            out.printf("Migrated: %d\n", summary.migrated());
            out.printf("Skipped:  %d\n", summary.skipped());
            out.printf("Errors:   %d\n", summary.errors());
            out.println("==================================================");
        }

        if (summary.hasErrors()) {
            err.println("Migration completed with errors.");
            System.exit(1);
        }
    }
}
