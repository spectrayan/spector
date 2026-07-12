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
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.synapse.agent.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Web search tool — performs web searches via DuckDuckGo HTML API.
 *
 * <p>Uses DuckDuckGo's lite HTML endpoint which doesn't require
 * an API key. Returns extracted text snippets from search results.</p>
 */
@Component
public class WebSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String name() { return "web_search"; }

    @Override
    public String description() {
        return "Search the web using DuckDuckGo. Returns text snippets from search results.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Search query"),
                        "maxResults", Map.of("type", "integer", "description", "Max results", "default", 5)
                ),
                "required", List.of("query")
        );
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.NETWORK;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        if (query == null || query.isBlank()) {
            return "Error: 'query' argument is required";
        }

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Spector/1.0 (Cognitive Memory Engine)")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // Extract snippets from the HTML (basic extraction)
            StringBuilder results = new StringBuilder();
            results.append("Web search results for: ").append(query).append("\n\n");

            // Extract result snippets between <a class="result__snippet"> tags
            int count = 0;
            int maxResults = arguments.containsKey("maxResults")
                    ? ((Number) arguments.get("maxResults")).intValue() : 5;
            int idx = 0;
            while (count < maxResults && idx < body.length()) {
                int snippetStart = body.indexOf("result__snippet", idx);
                if (snippetStart < 0) break;

                int textStart = body.indexOf(">", snippetStart);
                if (textStart < 0) break;
                textStart++;

                int textEnd = body.indexOf("</", textStart);
                if (textEnd < 0) break;

                String snippet = body.substring(textStart, textEnd)
                        .replaceAll("<[^>]*>", "")  // strip inner HTML tags
                        .trim();

                if (!snippet.isEmpty()) {
                    count++;
                    results.append(count).append(". ").append(snippet).append("\n\n");
                }
                idx = textEnd + 1;
            }

            if (count == 0) {
                results.append("No results found.");
            }

            log.debug("[WebSearch] '{}' → {} results", query, count);
            return results.toString();
        } catch (Exception e) {
            log.warn("[WebSearch] Failed: {}", e.getMessage());
            return "Error searching web: " + e.getMessage();
        }
    }
}
