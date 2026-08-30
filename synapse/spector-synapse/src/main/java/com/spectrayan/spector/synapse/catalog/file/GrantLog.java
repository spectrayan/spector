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
package com.spectrayan.spector.synapse.catalog.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spectrayan.spector.synapse.catalog.Grant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parser and writer for the append-only grants.jsonl format per ADR §19.
 */
public final class GrantLog {

    private static final Logger log = LoggerFactory.getLogger(GrantLog.class);

    private GrantLog() {
    }

    /**
     * Parses the grants file and returns a list of live (non-revoked, non-expired) grants.
     *
     * @param grantsFile the path to the grants.jsonl file
     * @param mapper the ObjectMapper to use for JSON parsing
     * @return a list of live grants
     * @throws IOException if an I/O error occurs
     */
    public static List<Grant> parseGrants(Path grantsFile, ObjectMapper mapper) throws IOException {
        List<Grant> grants = new ArrayList<>();
        Set<String> revokedGrantIds = new HashSet<>();
        
        if (!Files.exists(grantsFile)) {
            return grants;
        }

        List<Grant> allGrants = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(grantsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JsonNode node = mapper.readTree(line);
                if (node.has("type")) {
                    String type = node.get("type").asText();
                    if ("GRANT".equals(type)) {
                        Grant grant = mapper.treeToValue(node, Grant.class);
                        allGrants.add(grant);
                    } else if ("REVOKE".equals(type)) {
                        if (node.has("grantId")) {
                            revokedGrantIds.add(node.get("grantId").asText());
                        }
                    }
                }
            }
        }

        Instant now = Instant.now();
        for (Grant grant : allGrants) {
            if (!revokedGrantIds.contains(grant.grantId())) {
                if (grant.expiresAt() == null || grant.expiresAt().isAfter(now)) {
                    grants.add(grant);
                }
            }
        }

        return grants;
    }

    /**
     * Appends a GRANT line to the grants file.
     *
     * @param grantsFile the path to the grants.jsonl file
     * @param grant the grant to append
     * @param mapper the ObjectMapper to use for JSON serialization
     * @throws IOException if an I/O error occurs
     */
    public static void appendGrant(Path grantsFile, Grant grant, ObjectMapper mapper) throws IOException {
        ObjectNode node = mapper.valueToTree(grant);
        node.put("type", "GRANT");
        String line = mapper.writeValueAsString(node) + "\n";
        Files.writeString(grantsFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Appends a REVOKE line to the grants file.
     *
     * @param grantsFile the path to the grants.jsonl file
     * @param grantId the ID of the grant to revoke
     * @param mapper the ObjectMapper to use for JSON serialization
     * @throws IOException if an I/O error occurs
     */
    public static void appendRevoke(Path grantsFile, String grantId, ObjectMapper mapper) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "REVOKE");
        node.put("grantId", grantId);
        String line = mapper.writeValueAsString(node) + "\n";
        Files.writeString(grantsFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Compacts the grants file by rewriting it with only the live grants.
     *
     * @param grantsFile the path to the grants.jsonl file
     * @param liveGrants the list of live grants
     * @param mapper the ObjectMapper to use for JSON serialization
     * @throws IOException if an I/O error occurs
     */
    public static void compact(Path grantsFile, List<Grant> liveGrants, ObjectMapper mapper) throws IOException {
        Path temp = grantsFile.resolveSibling(grantsFile.getFileName() + ".tmp");
        try {
            for (Grant grant : liveGrants) {
                ObjectNode node = mapper.valueToTree(grant);
                node.put("type", "GRANT");
                String line = mapper.writeValueAsString(node) + "\n";
                Files.writeString(temp, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            Files.move(temp, grantsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to compact grants file: {}", grantsFile, e);
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    /**
     * Determines whether the grants file should be compacted.
     *
     * @param liveCount the number of live grants
     * @param revokeCount the number of revoked grants
     * @param fileSizeBytes the size of the grants file in bytes
     * @param maxNamespaces the maximum number of namespaces
     * @return true if the file should be compacted, false otherwise
     */
    public static boolean shouldCompact(int liveCount, int revokeCount, long fileSizeBytes, int maxNamespaces) {
        if (revokeCount > liveCount) {
            return true;
        }
        if (fileSizeBytes > 1_048_576L) { // 1 MiB
            return true;
        }
        if ((liveCount + revokeCount) > (4 * Math.max(16, maxNamespaces))) {
            return true;
        }
        return false;
    }
}
