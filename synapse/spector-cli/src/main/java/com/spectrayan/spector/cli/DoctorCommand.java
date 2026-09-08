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
import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Diagnostic command inspecting the local runtime environment, Java 25 & Panama Vector API,
 * CPU SIMD acceleration, directory permissions, Synapse daemon health, ONNX model loading,
 * and generating AI agent integration configurations.
 */
@Component
@Command(
        name = "doctor",
        description = "Diagnose the local environment, SIMD hardware acceleration, and runtime dependencies.",
        mixinStandardHelpOptions = true
)
public class DoctorCommand extends BaseCommand implements Callable<Integer> {

    @Option(names = {"--data-dir"}, description = "Target data directory to inspect (default: ~/.spector/data).")
    private String dataDir;

    @Option(names = {"--snippets"}, description = "Print copy-pasteable AI agent MCP config snippets.", defaultValue = "true")
    private boolean snippets = true;

    @Option(names = {"--verbose", "-v"}, description = "Show verbose debugging output and raw JVM execution blocks.")
    private boolean verbose = false;

    @Option(names = {"--print-config"}, description = "Output clean JSON configuration for an AI agent (claude or cursor).")
    private String printConfig;

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
        call();
    }

    @Override
    public Integer call() {
        // Handle --print-config directly if requested
        if (printConfig != null && !printConfig.isBlank()) {
            return handlePrintConfig(printConfig.trim().toLowerCase());
        }

        boolean hasFailures = false;
        Map<String, Object> report = new LinkedHashMap<>();

        // 1. Environment & Paths
        String spectorHome = System.getenv(String.join("_", "SPECTOR", "HOME"));
        if (spectorHome == null || spectorHome.isBlank()) {
            spectorHome = System.getProperty("user.home") + File.separator + ".spector";
        }
        String resolvedJarPath = "unknown (classpath execution)";
        try {
            resolvedJarPath = new File(DoctorCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (Exception ignored) {}

        Map<String, Object> envInfo = new LinkedHashMap<>();
        envInfo.put("spectorHome", spectorHome);
        envInfo.put("jarPath", resolvedJarPath);
        report.put("environment", envInfo);

        // 2. Java Runtime Diagnosis (Strict requirement: JDK 25+)
        int javaFeature = Runtime.version().feature();
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String javaHome = System.getProperty("java.home");
        boolean java25OrHigher = javaFeature >= 25;
        if (!java25OrHigher) {
            hasFailures = true;
        }

        Map<String, Object> javaInfo = new LinkedHashMap<>();
        javaInfo.put("version", javaVersion);
        javaInfo.put("feature", javaFeature);
        javaInfo.put("vendor", javaVendor);
        javaInfo.put("home", javaHome);
        javaInfo.put("supported", java25OrHigher);
        report.put("java", javaInfo);

        // 3. SIMD & Vector API Diagnosis
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

        // 4. Storage & Permissions Diagnosis (Must be writable)
        String resolvedDataDir = dataDir != null && !dataDir.isBlank()
                ? dataDir
                : spectorHome + File.separator + "data";
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

        if (!canWrite) {
            hasFailures = true;
        }

        Map<String, Object> storageInfo = new LinkedHashMap<>();
        storageInfo.put("path", resolvedDataDir);
        storageInfo.put("exists", dirExists);
        storageInfo.put("writable", canWrite);
        storageInfo.put("usableSpaceMb", usableSpaceMb);
        report.put("storage", storageInfo);

        // 5. In-Process ONNX Model Loading Diagnosis
        boolean onnxLoaded = false;
        String onnxDetail = "Not loaded";
        try {
            Class<?> clazz = Class.forName("dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel");
            var ctor = clazz.getConstructor();
            var instance = ctor.newInstance();
            if (instance != null) {
                onnxLoaded = true;
                onnxDetail = "all-MiniLM-L6-v2 (quantized, 384 dims, verified in-process)";
            }
        } catch (Throwable e) {
            onnxDetail = "Model load failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }

        Map<String, Object> onnxInfo = new LinkedHashMap<>();
        onnxInfo.put("loaded", onnxLoaded);
        onnxInfo.put("model", "all-MiniLM-L6-v2 (quantized)");
        onnxInfo.put("dimensions", 384);
        onnxInfo.put("detail", onnxDetail);
        report.put("onnx", onnxInfo);

        // 6. Synapse Daemon Reachability (Probing /actuator/health)
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

        // 7. Ollama Reachability (Optional)
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

        report.put("status", hasFailures ? "FAIL" : "OK");

        // 8. Output Formatting
        if (isJson()) {
            OutputFormatter.printJson(out(), report);
            return hasFailures ? 1 : 0;
        }

        // Pretty Terminal Output
        out().println();
        out().println("================================================================================");
        out().println("                     Spector System Diagnostics (Doctor)                        ");
        out().println("================================================================================");
        out().println();
        out().println("  SPECTOR_HOME         : " + spectorHome);
        out().println("  Binary JAR Location  : " + resolvedJarPath);
        out().println();

        printCheck("Java Runtime", java25OrHigher ? Status.OK : Status.FAIL,
                javaVersion + " (" + javaVendor + ") [Requires JDK 25+]");

        printCheck("SIMD Vector API", panamaVectorAvailable ? Status.OK : Status.WARN,
                panamaVectorAvailable ? simdInstructionSet + " via Project Panama" : "Incubator not active (--add-modules jdk.incubator.vector)");

        printCheck("Storage Permissions", (dirExists && canWrite) ? Status.OK : Status.FAIL,
                resolvedDataDir + " (" + usableSpaceMb + " MB available, writable=" + canWrite + ")");

        printCheck("In-Process ONNX", onnxLoaded ? Status.OK : Status.WARN, onnxDetail);

        printCheck("Synapse Daemon", synapseOnline ? Status.OK : Status.INFO,
                synapseOnline ? "ONLINE at " + synapseUrl + " (" + synapseStatus + ")" : "OFFLINE at " + synapseUrl + " (Run 'spector serve' to start)");

        printCheck("Ollama Daemon", ollamaOnline ? Status.OK : Status.INFO,
                ollamaOnline ? "ONLINE at " + ollamaUrl : "OFFLINE (Optional - in-process ONNX active)");

        out().println();
        out().println("--------------------------------------------------------------------------------");

        if (snippets) {
            printSnippets(synapseOnline, synapseUrl);
        }

        if (verbose) {
            printVerbose(resolvedJarPath);
        }

        return hasFailures ? 1 : 0;
    }

    private void printCheck(String label, Status status, String detail) {
        out().printf("  [%-4s]  %-22s : %s%n", status, label, detail);
    }

    private void printSnippets(boolean synapseOnline, String synapseUrl) {
        out().println();
        out().println("  [AI Agent Integration Configuration]");
        out().println();
        out().println("  Option 1: Zero-Install NPX MCP Runner (Preferred):");
        out().println("    npx -y @spectrayan/spector mcp");
        out().println();
        out().println("  Option 2: Standalone Local Binary (Installed):");
        out().println("    spector mcp");
        out().println();
        if (synapseOnline) {
            out().println("  Option 3: Active Daemon HTTP Endpoint (Online):");
            out().println("    " + synapseUrl + "/mcp");
            out().println();
        }
        out().println("  Claude Desktop / Cursor MCP JSON Configuration:");
        out().println("  {");
        out().println("    \"mcpServers\": {");
        out().println("      \"spector\": {");
        out().println("        \"command\": \"npx\",");
        out().println("        \"args\": [\"-y\", \"@spectrayan/spector\", \"mcp\"]");
        out().println("      }");
        out().println("    }");
        out().println("  }");
        out().println();
    }

    private void printVerbose(String jarPath) {
        out().println("  [Verbose Developer Execution]");
        out().println("  Raw JVM invocation with preview flags:");
        out().println("    java --add-modules jdk.incubator.vector \\");
        out().println("         --enable-native-access=ALL-UNNAMED \\");
        out().println("         --enable-preview \\");
        out().println("         -jar \"" + jarPath + "\" mcp");
        out().println();
    }

    private int handlePrintConfig(String target) {
        if ("claude".equals(target) || "cursor".equals(target) || "windsurf".equals(target)) {
            out().println("""
                    {
                      "mcpServers": {
                        "spector": {
                          "command": "npx",
                          "args": ["-y", "@spectrayan/spector", "mcp"]
                        }
                      }
                    }""");
            return 0;
        } else if ("http".equals(target)) {
            out().println("""
                    {
                      "mcpServers": {
                        "spector": {
                          "url": "http://127.0.0.1:7070/mcp"
                        }
                      }
                    }""");
            return 0;
        } else {
            err().println("Error: Unknown config target '" + target + "'. Supported: claude, cursor, windsurf, http");
            return 1;
        }
    }
}
