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

import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.persist.ReembedMigrator;
import com.spectrayan.spector.memory.persist.StoreEmbeddingIdentity;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import com.spectrayan.spector.spring.autoconfigure.SpectorEmbeddingAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CLI command group for store migrations ({@code spector migrate ...}).
 */
@Component
@Command(
        name = "migrate",
        description = "Migrate Spector persistence stores.",
        mixinStandardHelpOptions = true,
        subcommands = {
                MigrateCommand.ReembedSubcommand.class
        }
)
public class MigrateCommand implements Runnable {

    @ParentCommand
    SpectorCtl parent;

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /**
     * Subcommand {@code spector migrate reembed --from <data-dir> --to <data-dir> [--namespace <ns>] [--dry-run]}.
     */
    @Component
    @Command(
            name = "reembed",
            description = "Move a store to a new embedding model by re-embedding live records.",
            mixinStandardHelpOptions = true
    )
    public static class ReembedSubcommand implements Runnable {

        @ParentCommand
        MigrateCommand parent;

        @Spec
        CommandSpec spec;

        @Option(names = "--from", required = true, description = "Source data directory (opened strictly read-only).")
        Path fromDir;

        @Option(names = "--to", required = true, description = "Target data directory.")
        Path toDir;

        @Option(names = "--namespace", description = "Optional namespace to migrate.")
        String namespace;

        @Option(names = "--dry-run", description = "Report counts without writing to the target directory.")
        boolean dryRun;

        @Option(names = {"--config", "-c"}, description = "Optional path to spector.yml configuration file.")
        Path configFile;

        private ObjectProvider<EmbeddingProvider> embeddingProviderBean;
        private ObjectProvider<SpectorConfigProperties> configPropertiesBean;
        private ObjectProvider<DataEncryptor> dataEncryptorBean;
        private Environment environment;
        private EmbeddingProvider explicitEmbeddingProvider;

        public ReembedSubcommand() {}

        public ReembedSubcommand(EmbeddingProvider explicitEmbeddingProvider) {
            this.explicitEmbeddingProvider = explicitEmbeddingProvider;
        }

        @Autowired(required = false)
        void setDependencies(
                ObjectProvider<EmbeddingProvider> embeddingProviderBean,
                ObjectProvider<SpectorConfigProperties> configPropertiesBean,
                ObjectProvider<DataEncryptor> dataEncryptorBean,
                Environment environment) {
            this.embeddingProviderBean = embeddingProviderBean;
            this.configPropertiesBean = configPropertiesBean;
            this.dataEncryptorBean = dataEncryptorBean;
            this.environment = environment;
        }

        public void setEmbeddingProvider(EmbeddingProvider embeddingProvider) {
            this.explicitEmbeddingProvider = embeddingProvider;
        }

        @Override
        public void run() {
            EmbeddingProvider embedder = resolveEmbeddingProvider();
            SpectorConfigProperties springProps = configPropertiesBean != null ? configPropertiesBean.getIfAvailable() : null;
            com.spectrayan.spector.config.SpectorProperties coreProps =
                    springProps != null ? springProps.toSpectorProperties() : null;
            DataEncryptor encryptor = dataEncryptorBean != null ? dataEncryptorBean.getIfAvailable() : DataEncryptor.NOOP;

            ReembedMigrator.Result result = ReembedMigrator.migrate(
                    fromDir,
                    toDir,
                    namespace,
                    dryRun,
                    embedder,
                    coreProps,
                    encryptor
            );

            PrintWriter out = spec.commandLine().getOut();
            boolean jsonOutput = parent != null && parent.parent != null && parent.parent.json;
            if (jsonOutput) {
                printJson(out, result);
            } else {
                printSummary(out, result);
            }
        }

        private EmbeddingProvider resolveEmbeddingProvider() {
            if (explicitEmbeddingProvider != null) {
                return explicitEmbeddingProvider;
            }
            if (embeddingProviderBean != null) {
                EmbeddingProvider fromContext = embeddingProviderBean.getIfAvailable();
                if (fromContext != null) {
                    return fromContext;
                }
            }
            SpectorConfigProperties props = configPropertiesBean != null
                    ? configPropertiesBean.getIfAvailable()
                    : new SpectorConfigProperties();
            return new SpectorEmbeddingAutoConfiguration().createEmbeddingProvider(props, environment);
        }

        private static void printSummary(PrintWriter out, ReembedMigrator.Result result) {
            out.println("from=" + result.sourceDir());
            out.println("to=" + result.targetDir());
            out.println("namespace=" + result.namespace());
            out.println("dry-run=" + result.dryRun());
            out.println("source-embedder=" + formatIdentity(result.sourceIdentity()));
            out.println("target-embedder=" + formatIdentity(result.targetIdentity()));
            out.println("copied=" + result.copiedCount());
            String skippedBreakdown = result.skippedByReason().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
            out.println("skipped=" + result.totalSkipped() + " (" + skippedBreakdown + ")");
            out.println("fields-not-carried-over:");
            for (String field : result.fieldsNotCarriedOver()) {
                out.println("  - " + field);
            }
        }

        private static void printJson(PrintWriter out, ReembedMigrator.Result result) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"from\":\"").append(escapeJson(result.sourceDir().toString())).append("\",");
            sb.append("\"to\":\"").append(escapeJson(result.targetDir().toString())).append("\",");
            sb.append("\"namespace\":\"").append(escapeJson(result.namespace())).append("\",");
            sb.append("\"dryRun\":").append(result.dryRun()).append(",");
            sb.append("\"sourceEmbedder\":\"").append(escapeJson(formatIdentity(result.sourceIdentity()))).append("\",");
            sb.append("\"targetEmbedder\":\"").append(escapeJson(formatIdentity(result.targetIdentity()))).append("\",");
            sb.append("\"copied\":").append(result.copiedCount()).append(",");
            sb.append("\"totalSkipped\":").append(result.totalSkipped()).append(",");
            sb.append("\"skipped\":{");
            int idx = 0;
            for (Map.Entry<String, Integer> entry : result.skippedByReason().entrySet()) {
                if (idx++ > 0) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
            }
            sb.append("},");
            sb.append("\"fieldsNotCarriedOver\":[");
            for (int i = 0; i < result.fieldsNotCarriedOver().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(result.fieldsNotCarriedOver().get(i))).append("\"");
            }
            sb.append("]}");
            out.println(sb);
        }

        private static String formatIdentity(StoreEmbeddingIdentity.RecordedIdentity identity) {
            if (identity == null) {
                return "unknown";
            }
            return identity.model() + " (" + identity.dimensions() + "d)";
        }

        private static String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
