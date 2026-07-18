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

import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderDiscovery;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.ChatMessage;
import com.spectrayan.spector.provider.model.ImageContent;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Image captioning via Ollama VLM (Vision Language Model).
 *
 * <p>Reads an image file and sends it to the configured {@link LlmProvider}
 * with a captioning prompt. The VLM returns
 * a natural-language description that becomes the memory text.</p>
 *
 * <h3>Prerequisites</h3>
 * <ol>
 *   <li>Install Ollama: <a href="https://ollama.com/download">ollama.com/download</a></li>
 *   <li>Pull a vision model: {@code ollama pull moondream}</li>
 *   <li>Ensure the server is running</li>
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
 * <p>Thread-safe. The underlying provider handles concurrent requests.</p>
 */
public final class OllamaVisionExtractor implements SensoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(OllamaVisionExtractor.class);

    /** Default vision model. */
    private static final String DEFAULT_MODEL = "moondream";

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

    private final LlmProvider llm;

    /**
     * Creates an extractor with a custom LlmProvider.
     *
     * @param llm the LlmProvider to use
     */
    public OllamaVisionExtractor(LlmProvider llm) {
        this.llm = llm;
        log.info("OllamaVisionExtractor initialized with model: {}", llm.modelName());
    }

    /**
     * Creates an extractor with default settings (moondream).
     */
    public static OllamaVisionExtractor createDefault() {
        return create(DEFAULT_MODEL, null);
    }

    /**
     * Creates an extractor with a specific model.
     */
    public static OllamaVisionExtractor create(String model) {
        return create(model, null);
    }

    /**
     * Creates an extractor with a specific model and base URL.
     */
    public static OllamaVisionExtractor create(String model, String baseUrl) {
        String finalModel = model != null ? model : DEFAULT_MODEL;
        var config = ProviderConfig.local("vision-default", "ollama", finalModel, baseUrl);
        // Inject timeout property
        config = new ProviderConfig(config.name(), config.type(), config.model(), config.apiKey(),
                config.baseUrl(), config.dimensions(), Map.of("timeout", String.valueOf(DEFAULT_TIMEOUT.toSeconds())));
        
        var registry = ProviderDiscovery.discover(List.of(config));
        registry.activateGeneration("vision-default");
        var provider = registry.activeGeneration()
                .orElseThrow(() -> new IllegalStateException("Failed to initialize Ollama vision provider"));
        
        return new OllamaVisionExtractor(provider);
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

        byte[] imageBytes = Files.readAllBytes(source);

        long startNanos = System.nanoTime();
        String caption;

        try {
            var message = ChatMessage.user(
                new TextContent(CAPTIONING_PROMPT),
                new ImageContent(imageBytes, mimeType, null)
            );
            var request = LlmRequest.fromMessages(List.of(message));
            
            var options = GenerationOptions.builder()
                .temperature(0.2f)
                .maxTokens(512)
                .build();
                
            var response = llm.generate(request, options);
            caption = response.text();
        } catch (LlmProvider.GenerationException e) {
            throw new IOException("Vision extraction failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException("Vision extraction failed: " + e.getMessage(), e);
        }

        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        log.debug("VLM captioning: model={}, latency={}ms, imageSize={}KB, captionLen={}",
                model(), latencyMs, fileSize / 1024, caption.length());

        // Build metadata
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("modality", "IMAGE");
        metadata.put("source_uri", source.toAbsolutePath().toUri().toString());
        metadata.put("vlm_model", model());
        metadata.put("caption_length", String.valueOf(caption.length()));
        metadata.put("image_size_bytes", String.valueOf(fileSize));
        metadata.put("mime_type", mimeType);
        metadata.put("latency_ms", String.valueOf(latencyMs));

        String chunkId = source.getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._-]", "_");

        ExtractionChunk chunk = new ExtractionChunk(chunkId, caption, metadata);
        return Stream.of(chunk);
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public boolean isAvailable() {
        return llm.isAvailable();
    }

    /** Returns the vision model name. */
    public String model() {
        return llm.modelName();
    }
}
