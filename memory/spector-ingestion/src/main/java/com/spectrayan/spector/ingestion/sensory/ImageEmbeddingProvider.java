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

import java.io.IOException;
import java.nio.file.Path;

/**
 * SPI for direct image-to-vector embedding using CLIP, SigLIP, or similar
 * multi-modal embedding models.
 *
 * <p><b>This is a future extension point.</b> Unlike {@link OllamaVisionExtractor}
 * which produces text captions, this interface produces dense float[] vectors
 * directly from image pixels — enabling text-to-image and image-to-image
 * similarity search in a shared embedding space.</p>
 *
 * <h3>Key Differences from VLM Captioning</h3>
 * <table>
 *   <tr><th>Aspect</th><th>VLM Captioning</th><th>CLIP Embedding</th></tr>
 *   <tr><td>Output</td><td>Text caption</td><td>float[] vector</td></tr>
 *   <tr><td>Search</td><td>Text → Text similarity</td><td>Text → Image similarity</td></tr>
 *   <tr><td>Quality</td><td>Rich descriptions</td><td>Semantic matching</td></tr>
 *   <tr><td>Speed</td><td>Slow (LLM inference)</td><td>Fast (encoder-only)</td></tr>
 *   <tr><td>Storage</td><td>Text + text embedding</td><td>Image embedding only</td></tr>
 * </table>
 *
 * <h3>Future Implementations</h3>
 * <ul>
 *   <li><b>OnnxClipEmbedder</b> — ONNX Runtime with CLIP/SigLIP models</li>
 *   <li><b>OllamaClipEmbedder</b> — Ollama with CLIP-compatible models</li>
 *   <li><b>RemoteClipEmbedder</b> — REST API to a CLIP embedding server</li>
 * </ul>
 *
 * <h3>Design Notes</h3>
 * <p>When implemented, the memory system should store CLIP embeddings as a
 * separate vector alongside the text embedding (dual-vector storage). Recall
 * can then use either the text embedding space or the CLIP embedding space
 * depending on the query type.</p>
 *
 * @see SensoryExtractor
 */
public interface ImageEmbeddingProvider {

    /**
     * Embeds an image file into a dense vector.
     *
     * @param imagePath path to the image file
     * @return float[] vector in the CLIP/SigLIP embedding space
     * @throws IOException if the image cannot be read or embedded
     */
    float[] embedImage(Path imagePath) throws IOException;

    /**
     * Embeds raw image bytes into a dense vector.
     *
     * @param imageBytes raw image bytes (JPEG, PNG, etc.)
     * @param mimeType   MIME type of the image
     * @return float[] vector in the CLIP/SigLIP embedding space
     * @throws IOException if the image cannot be embedded
     */
    float[] embedImage(byte[] imageBytes, String mimeType) throws IOException;

    /**
     * Embeds a text query into the same vector space as images.
     *
     * <p>This enables text-to-image similarity search — the text embedding
     * can be compared directly against image embeddings using cosine similarity.</p>
     *
     * @param text the text query
     * @return float[] vector in the CLIP/SigLIP embedding space
     * @throws IOException if embedding fails
     */
    float[] embedText(String text) throws IOException;

    /**
     * Returns the dimensionality of the embedding vectors.
     *
     * @return embedding dimensions (e.g., 512 for CLIP ViT-B/32, 768 for SigLIP)
     */
    int dimensions();

    /**
     * Returns the model name.
     *
     * @return model identifier (e.g., "ViT-B/32", "siglip-base-patch16-224")
     */
    String modelName();

    /**
     * Returns whether the embedding provider is available and ready.
     *
     * @return true if the model is loaded and ready for inference
     */
    boolean isAvailable();
}
