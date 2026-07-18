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
package com.spectrayan.spector.ingestion.sensory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Image captioning via Ollama VLM (Vision Language Model).
 *
 * <p>Reads an image file, Base64-encodes it, and sends it to the Ollama
 * {@code /api/generate} endpoint with a captioning prompt. The VLM returns
 * a natural-language description that becomes the memory text.</p>
 *
 * <h3>Prerequisites</h3>
 * <ol>
 *   <li>Install Ollama: <a href="https://ollama.com/download">ollama.com/download</a></li>
 *   <li>Pull a vision model: {@code ollama pull moondream}</li>
 *   <li>Ensure the server is running (default: {@code http://localhost:11434})</li>
 * </ol>
 *
 * <h3>Supported Models</h3>
 * <ul>
 *   <li>{@code moondream} — smallest/fastest, good for quick captioning</li>
 *   <li>{@code llama3.2-vision} — balanced quality and speed</li>
 *   <li>{@code llava:13b} — richest captions, slowest</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Thread-safe. The underlying {@link HttpClient} handles concurrent requests.</p>
 */
public final class OllamaVisionExtractor implements SensoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(OllamaVisionExtractor.class);

    /** Default vision model. */
    private static final String DEFAULT_MODEL = "moondream";

    /** Default Ollama base URL. */
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    /** Default timeout for vision inference (images take longer). */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    /** Maximum image file size (20 MB). */
    private static final long MAX_IMAGE_SIZE_BYTES = 20 * 1024 * 1024;

    /** Captioning prompt — instructs the VLM to produce a rich description. */
    private static final String CAPTIONING_PROMPT =
            "Describe this image in detail. Include: " +
            "1) All visible objects and their spatial relationships, " +
            "2) Any visible text (OCR), " +
            "3) People and their actions (if present), " +
            "4) Colors, lighting, and composition, " +
            "5) The overall mood or emotional tone.";

    /** Supported image MIME types. */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "image/webp", "image/bmp", "image/tiff"
    );

    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final URI generateUri;
    private final URI tagsUri;

    /**
     * Creates an extractor with full configuration.
     *
     * @param model   the Ollama vision model name (e.g., "moondream")
     * @param baseUrl the Ollama server base URL
     * @param timeout HTTP request timeout
     */
    public OllamaVisionExtractor(String model, String baseUrl, Duration timeout) {
        this.model = model != null ? model : DEFAULT_MODEL;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
        this.generateUri = URI.create(this.baseUrl + "/api/generate");
        this.tagsUri = URI.create(this.baseUrl + "/api/tags");
        log.info("OllamaVisionExtractor initialized: model={}, baseUrl={}, timeout={}s",
                this.model, this.baseUrl, this.timeout.toSeconds());
    }

    /**
     * Creates an extractor with default settings (moondream on localhost).
     */
    public static OllamaVisionExtractor createDefault() {
        return new OllamaVisionExtractor(DEFAULT_MODEL, DEFAULT_BASE_URL, DEFAULT_TIMEOUT);
    }

    /**
     * Creates an extractor with a specific model.
     */
    public static OllamaVisionExtractor create(String model) {
        return new OllamaVisionExtractor(model, DEFAULT_BASE_URL, DEFAULT_TIMEOUT);
    }

    /**
     * Creates an extractor with a specific model and base URL.
     */
    public static OllamaVisionExtractor create(String model, String baseUrl) {
        return new OllamaVisionExtractor(model, baseUrl, DEFAULT_TIMEOUT);
    }

    @Override
    public Stream<ExtractionChunk> extract(Path source, String mimeType) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("source path must not be null");
        }
        if (!supports(mimeType)) {
            log.debug("Unsupported MIME type '{}', returning empty stream", mimeType);
            return Stream.empty();
        }
        if (!Files.exists(source)) {
            throw new IOException("Image file not found: " + source);
        }

        long fileSize = Files.size(source);
        if (fileSize > MAX_IMAGE_SIZE_BYTES) {
            throw new IOException("Image file too large: " + fileSize + " bytes (max: "
                    + MAX_IMAGE_SIZE_BYTES + " bytes)");
        }
        if (fileSize == 0) {
            throw new IOException("Image file is empty: " + source);
        }

        // Read and Base64-encode the image
        byte[] imageBytes = Files.readAllBytes(source);
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // Build Ollama /api/generate request with images field
        String requestBody = buildVisionRequest(base64Image);
        long startNanos = System.nanoTime();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(generateUri)
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Ollama returned HTTP " + response.statusCode()
                        + ": " + truncate(response.body(), 500));
            }

            String caption = parseResponseField(response.body());
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            log.debug("VLM captioning: model={}, latency={}ms, imageSize={}KB, captionLen={}",
                    model, latencyMs, fileSize / 1024, caption.length());

            // Build metadata
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("modality", "IMAGE");
            metadata.put("source_uri", source.toAbsolutePath().toUri().toString());
            metadata.put("vlm_model", model);
            metadata.put("caption_length", String.valueOf(caption.length()));
            metadata.put("image_size_bytes", String.valueOf(fileSize));
            metadata.put("mime_type", mimeType);
            metadata.put("latency_ms", String.valueOf(latencyMs));

            String chunkId = source.getFileName().toString()
                    .replaceAll("[^a-zA-Z0-9._-]", "_");

            ExtractionChunk chunk = new ExtractionChunk(chunkId, caption, metadata);
            return Stream.of(chunk);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Vision extraction interrupted", e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Vision extraction failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(tagsUri)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Ollama vision availability check failed: {}", e.getMessage());
            return false;
        }
    }

    /** Returns the vision model name. */
    public String model() {
        return model;
    }

    // ─────────────── Request building ───────────────

    private String buildVisionRequest(String base64Image) {
        // Manual JSON construction — avoids Jackson dependency in spector-ingestion
        return "{" +
                "\"model\":\"" + escapeJson(model) + "\"," +
                "\"prompt\":\"" + escapeJson(CAPTIONING_PROMPT) + "\"," +
                "\"images\":[\"" + base64Image + "\"]," +
                "\"stream\":false," +
                "\"options\":{\"temperature\":0.2,\"num_predict\":512}" +
                "}";
    }

    // ─────────────── Response parsing ───────────────

    /**
     * Extracts the "response" field from the Ollama JSON response.
     *
     * <p>Uses simple string scanning to avoid Jackson dependency.</p>
     */
    private static String parseResponseField(String json) throws IOException {
        // Find "response":"..." in the JSON
        String marker = "\"response\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            // Try without quotes for null/boolean
            marker = "\"response\":";
            start = json.indexOf(marker);
            if (start < 0) {
                throw new IOException("No 'response' field in Ollama generate response");
            }
        }

        start += marker.length();
        if (start >= json.length()) {
            throw new IOException("Truncated 'response' field in Ollama generate response");
        }

        // If value starts with quote, parse escaped string
        if (json.charAt(start - 1) == '"') {
            // Already inside the quoted string — find end
            // Simple unescaping: handle \" and \\
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case '"' -> { sb.append('"'); i++; }
                        case '\\' -> { sb.append('\\'); i++; }
                        case 'n' -> { sb.append('\n'); i++; }
                        case 'r' -> { sb.append('\r'); i++; }
                        case 't' -> { sb.append('\t'); i++; }
                        default -> sb.append(c);
                    }
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString().strip();
        }

        // Non-string value — shouldn't happen for response, but handle gracefully
        int end = json.indexOf(',', start);
        if (end < 0) end = json.indexOf('}', start);
        if (end < 0) end = json.length();
        return json.substring(start, end).strip();
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "<null>";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
