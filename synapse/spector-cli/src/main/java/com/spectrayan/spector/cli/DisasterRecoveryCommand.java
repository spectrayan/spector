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

import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties;
import com.spectrayan.spector.synapse.dr.DisasterRecoveryExporter;
import com.spectrayan.spector.synapse.dr.DisasterRecoveryRestorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * CLI command group for Cell Disaster Recovery, standby cell promotion, and snapshot rehydration
 * (ADR-0034 §11.2, §14, §16, Phase 6, Req R4.1, R3.1, G30).
 *
 * <p>Invariant V3: Cell promotion is an explicit HUMAN decision requiring an auditable trigger.
 * Automatic promotion across cells is strictly forbidden to prevent undetectable multi-region split-brain.</p>
 */
@Component
@Command(
        name = "dr",
        description = "Manage cell disaster recovery, promotions, and standby restore operations.",
        mixinStandardHelpOptions = true,
        subcommands = {
                DisasterRecoveryCommand.PromoteCellSubcommand.class,
                DisasterRecoveryCommand.RestoreNamespaceSubcommand.class,
                DisasterRecoveryCommand.StatusSubcommand.class
        }
)
public class DisasterRecoveryCommand extends BaseCommand {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Override
    public void run() {
        spec.commandLine().usage(out());
    }

    @Component
    @Command(
            name = "promote",
            description = "Promote this standby cell to primary authority (AUDITABLE HUMAN DECISION, Req R4.1, G30).",
            mixinStandardHelpOptions = true
    )
    public static class PromoteCellSubcommand extends BaseCommand {

        @Option(names = {"--cell"}, description = "Target cell ID to promote.", required = true)
        private String cellId;

        @Option(names = {"--reason"}, description = "Operational justification / incident ticket.", required = true)
        private String reason;

        @Option(names = {"--force"}, description = "Acknowledge that promotion is irreversible without data migration.")
        private boolean force;

        @Option(
                names = {"--audit-file"},
                description = "Append-only file path to persist promotion audit records (G30).",
                defaultValue = "dr-promotion-audit.log"
        )
        private String auditFile = "dr-promotion-audit.log";

        @Override
        public void run() {
            if (!force) {
                err().println("ERROR: Cell promotion requires '--force' acknowledging that promotion is a one-way cutover (Req R4.1, R4.8).");
                return;
            }

            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("action", "CELL_PROMOTION");
            audit.put("cellId", cellId);
            audit.put("reason", reason);
            audit.put("promotedAt", Instant.now().toString());
            audit.put("operator", System.getProperty("user.name", "unknown"));
            audit.put("authority", "ACTIVE_PRIMARY");
            audit.put("warning", "Dead cell must be decommissioned or isolated to avoid split-brain upon network reconnection (Req R4.7).");

            // G30: Persist promote audit record to an append-only store before acknowledging
            Path auditPath = Path.of(auditFile);
            try {
                if (auditPath.getParent() != null) {
                    Files.createDirectories(auditPath.getParent());
                }
                String auditJson = MAPPER.writeValueAsString(audit) + System.lineSeparator();
                Files.writeString(auditPath, auditJson, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                audit.put("auditPersistedTo", auditPath.toAbsolutePath().toString());
            } catch (IOException e) {
                err().println("ERROR: Failed to persist promotion audit record to " + auditPath + ": " + e.getMessage());
                return;
            }

            if (isJson()) {
                OutputFormatter.printJson(out(), audit);
            } else {
                out().println("================================================================================");
                out().println("               CELL PROMOTION AUDIT RECORD (Req R4.1, V3, G30)                  ");
                out().println("================================================================================");
                out().println("Cell ID:     " + cellId);
                out().println("Reason:      " + reason);
                out().println("Operator:    " + audit.get("operator"));
                out().println("Timestamp:   " + audit.get("promotedAt"));
                out().println("Authority:   ACTIVE_PRIMARY");
                out().println("Audit Log:   " + audit.get("auditPersistedTo"));
                out().println("WARNING:     Reverse path requires a structured data migration, not symmetric undo.");
                out().println("================================================================================");
            }
        }
    }

    @Component
    @Command(
            name = "restore",
            description = "Hydrate and restore a namespace from S3 disaster recovery snapshots (G30).",
            mixinStandardHelpOptions = true
    )
    public static class RestoreNamespaceSubcommand extends BaseCommand {

        @Option(names = {"--tenant"}, description = "Tenant ID.", defaultValue = "untenanted")
        private String tenantId = "untenanted";

        @Option(names = {"--namespace"}, description = "Namespace ID to restore.", required = true)
        private String namespaceId;

        @Option(names = {"--epoch"}, description = "Snapshot epoch sequence or 'latest'.", defaultValue = "latest")
        private String epoch = "latest";

        @Option(names = {"--target-dir"}, description = "Target directory to rehydrate namespace into.")
        private String targetDir;

        @Option(names = {"--jurisdiction"}, description = "Tenant jurisdiction for data residency validation.")
        private String jurisdiction;

        @Autowired(required = false)
        private DisasterRecoveryRestorer restorer;

        @Autowired(required = false)
        private SynapseProperties synapseProperties;

        public RestoreNamespaceSubcommand() {}

        public RestoreNamespaceSubcommand(DisasterRecoveryRestorer restorer) {
            this.restorer = restorer;
        }

        public RestoreNamespaceSubcommand(DisasterRecoveryRestorer restorer, SynapseProperties synapseProperties) {
            this.restorer = restorer;
            this.synapseProperties = synapseProperties;
        }

        @Override
        public void run() {
            if (restorer == null) {
                err().println("ERROR: Disaster recovery restorer is not configured or unavailable (Req R3.1–R3.8, G30).");
                return;
            }

            Path targetBaseDir;
            if (targetDir != null && !targetDir.isBlank()) {
                targetBaseDir = Path.of(targetDir);
            } else if (synapseProperties != null) {
                targetBaseDir = synapseProperties.remembererRoot();
            } else {
                targetBaseDir = SpectorPropertyConstants.DEFAULT_MEMORY_PERSISTENCE_PATH;
            }

            long targetEpoch;
            if ("latest".equalsIgnoreCase(epoch)) {
                List<Long> selectable = restorer.discoverSelectableEpochs(tenantId, namespaceId);
                if (selectable.isEmpty()) {
                    err().printf("ERROR: No selectable snapshot epochs found for tenant=%s, namespace=%s (G32, Req R2.6)%n",
                            tenantId, namespaceId);
                    return;
                }
                targetEpoch = selectable.get(selectable.size() - 1);
            } else {
                try {
                    targetEpoch = Long.parseLong(epoch);
                } catch (NumberFormatException e) {
                    err().println("ERROR: Invalid epoch sequence parameter: " + epoch);
                    return;
                }
            }

            try {
                DisasterRecoveryRestorer.RestoreResult result = restorer.restoreNamespace(
                        tenantId,
                        namespaceId,
                        targetEpoch,
                        targetBaseDir,
                        jurisdiction
                );

                if (!result.success()) {
                    err().printf("ERROR: Restore refused for namespace %s: %s%n", namespaceId, result.refusalReason());
                    return;
                }

                if (isJson()) {
                    OutputFormatter.printJson(out(), result);
                } else {
                    out().println("================================================================================");
                    out().println("               DISASTER RECOVERY RESTORE COMPLETE (Req R3.1–R3.8, G30)          ");
                    out().println("================================================================================");
                    out().println("Namespace ID: " + result.namespaceId());
                    out().println("Tenant ID:    " + result.tenantId());
                    out().println("Epoch:        " + result.epoch());
                    out().println("Restored HWM: " + result.hwm());
                    out().println("Artifacts:    " + result.verifiedArtifactCount() + " verified");
                    out().println("Location:     " + result.restoredDir());
                    out().println("Elapsed:      " + result.elapsedMs() + " ms");
                    out().println("================================================================================");
                }
            } catch (Exception e) {
                err().println("ERROR: Restore execution failed: " + e.getMessage());
            }
        }
    }

    @Component
    @Command(
            name = "status",
            description = "Show DR export lag, backup counts, and disaster readiness posture (G30).",
            mixinStandardHelpOptions = true
    )
    public static class StatusSubcommand extends BaseCommand {

        @Autowired(required = false)
        private DisasterRecoveryProperties drProperties;

        @Autowired(required = false)
        private DisasterRecoveryExporter drExporter;

        public StatusSubcommand() {}

        public StatusSubcommand(DisasterRecoveryProperties drProperties, DisasterRecoveryExporter drExporter) {
            this.drProperties = drProperties;
            this.drExporter = drExporter;
        }

        @Override
        public void run() {
            boolean drConfigured = drProperties != null;
            boolean drEnabled = drConfigured && drProperties.isExportEnabled();
            String standbyMode = drConfigured ? drProperties.getStandbyMode() : "NOT_CONFIGURED";
            Long exportIntervalSeconds = drConfigured ? drProperties.getExportIntervalSeconds() : null;

            OptionalLong measuredRpo = (drExporter != null) ? drExporter.getMeasuredP99Rpo() : OptionalLong.empty();

            String posture;
            if (!drConfigured) {
                posture = "NOT_CONFIGURED";
            } else if (!drEnabled) {
                posture = "DISABLED";
            } else if (measuredRpo.isPresent()) {
                posture = "READY";
            } else {
                posture = "UNMEASURED (no export intervals recorded yet)";
            }

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("drConfigured", drConfigured);
            status.put("drEnabled", drEnabled);
            status.put("standbyMode", standbyMode);
            status.put("exportIntervalSeconds", exportIntervalSeconds != null ? exportIntervalSeconds : "NOT_CONFIGURED");
            status.put("measuredP99RpoSeconds", measuredRpo.isPresent() ? measuredRpo.getAsLong() : "UNKNOWN");
            status.put("posture", posture);
            status.put("rtoTargetMinutes", 30);
            status.put("residencyControl", "ORG_TO_CELL_PIN");

            if (isJson()) {
                OutputFormatter.printJson(out(), status);
            } else {
                out().println("Disaster Recovery Posture: " + posture);
                out().println("Standby Posture:          " + standbyMode);
                out().println("Export Interval:          " + (exportIntervalSeconds != null ? exportIntervalSeconds + "s" : "NOT_CONFIGURED"));
                out().println("Measured RPO:             " + (measuredRpo.isPresent() ? measuredRpo.getAsLong() + "s" : "UNKNOWN"));
                out().println("RTO Target:               0-30m (Rehearsed Drill)");
            }
        }
    }
}
