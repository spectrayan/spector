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
package com.spectrayan.spector.connector.sink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Enterprise Dead Letter Queue (DLQ) processor.
 * Routes failed or corrupt connector ingestion payloads to isolated disk storage for admin audit.
 */
public final class CognitiveDlq {

    private static final String DLQ_ROOT = "D:/git/spector-enterprise/data/dlq/";

    private CognitiveDlq() {}

    /**
     * Routes a failed ingestion exchange payload to the Dead Letter Queue.
     *
     * @param tenantId     active tenant ID
     * @param routeId      originating route ID
     * @param docId        target document/chunk ID
     * @param content      original un-redacted/redacted text content
     * @param errorMessage error details/stacktrace summary
     */
    public static void routeToDlq(String tenantId, String routeId, String docId, String content, String errorMessage) {
        try {
            Path dlqDir = Paths.get(DLQ_ROOT, tenantId);
            Files.createDirectories(dlqDir);

            // Sanitize doc ID for safe filename
            String safeDocId = docId.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
            String fileName = String.format("%s_%s_%d.json", routeId, safeDocId, System.currentTimeMillis());
            Path filePath = dlqDir.resolve(fileName);

            String json = String.format("""
                {
                  "tenantId": "%s",
                  "routeId": "%s",
                  "docId": "%s",
                  "timestamp": "%s",
                  "errorMessage": "%s",
                  "content": "%s"
                }
                """,
                escapeJson(tenantId),
                escapeJson(routeId),
                escapeJson(docId),
                Instant.now().toString(),
                escapeJson(errorMessage),
                escapeJson(content)
            );

            Files.writeString(filePath, json);
        } catch (IOException e) {
            System.err.println("[DLQ] Failed to write to Dead Letter Queue: " + e.getMessage());
        }
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\r", "\\r")
                  .replace("\n", "\\n")
                  .replace("\t", "\\t");
    }
}
