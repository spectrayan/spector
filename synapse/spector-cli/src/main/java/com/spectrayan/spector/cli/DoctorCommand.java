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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Diagnostic command inspecting the local runtime environment, Java 25 & Panama Vector API,
 * CPU SIMD acceleration, directory permissions, Synapse daemon health, Ollama availability,
 * and generating AI agent integration snippets.
 */
@Component
@Command(
        name = "doctor",
        description = "Diagnose the local environment, SIMD hardware acceleration, and runtime dependencies.",
        mixinStandardHelpOptions = true
)
public class DoctorCommand extends BaseCommand {

    @Option(names = {"--data-dir"}, description = "Target data directory to inspect (default: ~/.spector/data).")
    private String dataDir;

    @Option(names = {"--snippets"}, description = "Print copy-pasteable AI agent MCP config snippets.", defaultValue = "true")
    private boolean snippets = true;

    private enum Status {
        OK("OK"),
        INFO("INFO"),
        WARN("WARN"),
        FAIL("FAIL");

        private final String tag;

        Status(String tag) {
            this.tag = tag;
        }

        @Override
        public String toString() {
            return tag;
        }
    }

    @Override
    public void run() {
        Map<String, Object> report = new LinkedHashMap<>();

        // 1. Java Runtime Diagnosis
        int javaFeature = Runtime.version().feature();
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String javaHome = System.getProperty("java.home");
        boolean java25OrHigher = javaFeature >= 25;

        Map<String, Object> javaInfo = new LinkedHashMap<>();
        javaInfo.put("version", javaVersion);
        javaInfo.put("feature", javaFeature);
        javaInfo.put("vendor", javaVendor);
        javaInfo.put("home", javaHome);
        javaInfo.put("supported", java25OrHigher);
        report.put("java", javaInfo);

        // 2. SIMD & Vector API Diagnosis
        boolean panamaVectorAvailable = false;
        int vectorBitSize = 0;
        int laneCount = 0;
        String simdInstructionSet = "None (Scalar Fallback)";
        try {
            vectorBitSize = com.spectrayan.spector.core.simd.SimdCapability.vectorBitSize();
            laneCount = com.spectrayan.spector.core.simd.SimdCapability.laneCount();
            panamaVectorAvailable = true;
            if (vectorBitSize >= 512) {
                simdInstructionSet = "AVX-512 (" + vectorBitSize + "-bit, " + laneCount + " lanes)";
            } else if (vectorBitSize >= 256) {
                simdInstructionSet = "AVX2 / NEON (" + vectorBitSize + "-bit, " + laneCount + " lanes)";
            } else if (vectorBitSize >= 128) {
                simdInstructionSet = "SSE4.2 / NEON (" + vectorBitSize + "-bit, " + laneCount + " lanes)";
            } else {
                simdInstructionSet = "Vector API (" + vectorBitSize + "-bit, " + laneCount + " lanes)";
            }
        } catch (Throwable ignored) {}

        Map<String, Object> simdInfo = new LinkedHashMap<>();
        simdInfo.put("panamaVectorAvailable", panamaVectorAvailable);
        simdInfo.put("vectorBitSize", vectorBitSize);
        simdInfo.put("instructionSet", simdInstructionSet);
        report.put("simd", simdInfo);

        // 3. Storage & Permissions Diagnosis
        String resolvedDataDir = dataDir != null && !dataDir.isBlank()
                ? dataDir
                : System.getProperty("user.home") + File.separator + ".spector" + File.separator + "data";
        Path storagePath = Paths.get(resolvedDataDir);
        boolean dirExists = Files.exists(storagePath);
        boolean canWrite = false;
        long usableSpaceMb = 0;
        try {
            if (!dirExists) {
                Files.createDirectories(storagePath);
                dirExists = true;
            }
            Path testFile = storagePath.resolve(".doctor_test_" + System.currentTimeMillis());
            Files.writeString(testFile, "test");
            Files.deleteIfExists(testFile);
            canWrite = true;
            usableSpaceMb = storagePath.toFile().getUsableSpace() / (1024 * 1024);
        } catch (Exception ignored) {}

        Map<String, Object> storageInfo = new LinkedHashMap<>();
        storageInfo.put("path", resolvedDataDir);
        storageInfo.put("exists", dirExists);
        storageInfo.put("writable", canWrite);
        storageInfo.put("usableSpaceMb", usableSpaceMb);
        report.put("storage", storageInfo);

        // 4. In-Process ONNX Fallback Diagnosis
        boolean onnxAvailable = false;
        try {
            Class.forName("dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel");
            onnxAvailable = true;
        } catch (Throwable ignored) {}

        Map<String, Object> onnxInfo = new LinkedHashMap<>();
        onnxInfo.put("available", onnxAvailable);
        onnxInfo.put("model", "all-MiniLM-L6-v2 (quantized)");
        onnxInfo.put("dimensions", 384);
        report.put("onnx", onnxInfo);

        // 5. Synapse Daemon Reachability
        String synapseUrl = "http://" + getHost() + ":" + getPort();
        boolean synapseOnline = false;
        String synapseStatus = "OFFLINE";
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(800))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(synapseUrl + "/actuator/health"))
                    .timeout(Duration.ofMillis(1200))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                synapseOnline = true;
                synapseStatus = response.body().contains("UP") ? "UP" : "ONLINE";
            }
        } catch (Exception ignored) {}

        Map<String, Object> synapseInfo = new LinkedHashMap<>();
        synapseInfo.put("url", synapseUrl);
        synapseInfo.put("online", synapseOnline);
        synapseInfo.put("status", synapseStatus);
        report.put("synapse", synapseInfo);

        // 6. Ollama Reachability (Optional)
        String ollamaUrl = "http://localhost:11434";
        boolean ollamaOnline = false;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(800))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/version"))
                    .timeout(Duration.ofMillis(1200))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ollamaOnline = true;
            }
        } catch (Exception ignored) {}

        Map<String, Object> ollamaInfo = new LinkedHashMap<>();
        ollamaInfo.put("url", ollamaUrl);
        ollamaInfo.put("online", ollamaOnline);
        report.put("ollama", ollamaInfo);

        // 7. Output Formatting
        if (isJson()) {
            OutputFormatter.printJson(out(), report);
            return;
        }

        // Pretty Terminal Output
        out().println();
        out().println("================================================================================");
        out().println("                     Spector System Diagnostics (Doctor)                        ");
        out().println("================================================================================");
        out().println();

        printCheck("Java Runtime", java25OrHigher ? Status.OK : Status.WARN,
                javaVersion + " (" + javaVendor + ") [Requires JDK 25+]");

        printCheck("SIMD Vector API", panamaVectorAvailable ? Status.OK : Status.WARN,
                panamaVectorAvailable ? simdInstructionSet + " via Project Panama" : "Incubator not active (--add-modules jdk.incubator.vector)");

        printCheck("Storage Permissions", (dirExists && canWrite) ? Status.OK : Status.FAIL,
                resolvedDataDir + " (" + usableSpaceMb + " MB available, writable=" + canWrite + ")");

        printCheck("In-Process ONNX", onnxAvailable ? Status.OK : Status.WARN,
                onnxAvailable ? "all-MiniLM-L6-v2 (384 dims, zero external dependencies)" : "Model not found on classpath");

        printCheck("Synapse Daemon", synapseOnline ? Status.OK : Status.INFO,
                synapseOnline ? "ONLINE at " + synapseUrl + " (" + synapseStatus + ")" : "OFFLINE at " + synapseUrl + " (Run 'spector-synapse' to start)");

        printCheck("Ollama Daemon", ollamaOnline ? Status.OK : Status.INFO,
                ollamaOnline ? "ONLINE at " + ollamaUrl : "OFFLINE (Optional - fallback to ONNX active)");

        out().println();
        out().println("--------------------------------------------------------------------------------");

        if (snippets) {
            printSnippets();
        }
    }

    private void printCheck(String label, Status status, String detail) {
        out().printf("  [%-4s]  %-22s : %s%n", status, label, detail);
    }

    private void printSnippets() {
        out().println();
        out().println("  [AI Agent Integration Configuration]");
        out().println();
        out().println("  Add to Claude Desktop (claude_desktop_config.json) or Cursor (.cursor/mcp.json):");
        out().println();
        out().println("  {");
        out().println("    \"mcpServers\": {");
        out().println("      \"spector\": {");
        out().println("        \"command\": \"java\",");
        out().println("        \"args\": [");
        out().println("          \"--add-modules\", \"jdk.incubator.vector\",");
        out().println("          \"--enable-preview\",");
        out().println("          \"-jar\", \"<path-to-spector-cli.jar>\",");
        out().println("          \"mcp\"");
        out().println("        ]");
        out().println("      }");
        out().println("    }");
        out().println("  }");
        out().println();
    }
}
