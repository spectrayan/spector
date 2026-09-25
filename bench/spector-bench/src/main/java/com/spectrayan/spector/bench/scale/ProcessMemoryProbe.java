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
package com.spectrayan.spector.bench.scale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Diagnostic probe for capturing process RSS (Resident Set Size), JVM heap, and disk utilization.
 */
public final class ProcessMemoryProbe {

    private static final Logger log = LoggerFactory.getLogger(ProcessMemoryProbe.class);

    private ProcessMemoryProbe() {}

    /**
     * Estimates or queries the operating system Resident Set Size (RSS) in megabytes.
     */
    public static double getProcessRssMb() {
        long bytes = getProcessRssBytes();
        return bytes / (1024.0 * 1024.0);
    }

    /**
     * Queries the process RSS in bytes via OS commands or /proc/self/status.
     */
    public static long getProcessRssBytes() {
        try {
            long pid = ProcessHandle.current().pid();
            String os = System.getProperty("os.name", "").toLowerCase();

            // Linux: Read /proc/self/status
            if (os.contains("linux")) {
                Path statusPath = Path.of("/proc/self/status");
                if (Files.exists(statusPath)) {
                    List<String> lines = Files.readAllLines(statusPath);
                    for (String line : lines) {
                        if (line.startsWith("VmRSS:")) {
                            String[] parts = line.split("\\s+");
                            if (parts.length >= 2) {
                                return Long.parseLong(parts[1]) * 1024L; // in kB -> bytes
                            }
                        }
                    }
                }
            }

            // macOS / BSD / POSIX: ps -o rss= -p <pid>
            ProcessBuilder pb = new ProcessBuilder("ps", "-o", "rss=", "-p", String.valueOf(pid));
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return Long.parseLong(line.trim()) * 1024L; // ps reports kB
                }
            }
        } catch (Exception e) {
            log.debug("Process RSS query non-critical error: {}", e.getMessage());
        }

        // Fallback: total allocated memory in JVM
        return Runtime.getRuntime().totalMemory();
    }

    /**
     * Returns JVM heap memory used in megabytes.
     */
    public static double getUsedHeapMb() {
        long usedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return usedBytes / (1024.0 * 1024.0);
    }

    /**
     * Computes recursive directory size in megabytes.
     */
    public static double getDirectorySizeMb(Path directory) {
        if (directory == null || !Files.exists(directory)) return 0.0;
        try (var stream = Files.walk(directory)) {
            long totalBytes = stream
                    .filter(p -> p.toFile().isFile())
                    .mapToLong(p -> p.toFile().length())
                    .sum();
            return totalBytes / (1024.0 * 1024.0);
        } catch (Exception e) {
            log.warn("Failed to compute directory size for {}: {}", directory, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Resolves host hardware summary profile.
     */
    public static String getHardwareProfile() {
        int cores = Runtime.getRuntime().availableProcessors();
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String os = System.getProperty("os.name") + " " + System.getProperty("os.arch") + " (" + System.getProperty("os.version") + ")";
        String javaVersion = System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
        return String.format("%s, %d CPU cores, %d MB max heap, Java %s", os, cores, maxHeapMb, javaVersion);
    }
}
