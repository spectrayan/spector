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
package com.spectrayan.spector.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A stable, secret-free identity for an effective {@link ProviderConfig}.
 *
 * <p>Two configurations that produce the same fingerprint will produce providers that behave
 * identically, and may therefore share one instance. Two configurations that differ in any way a
 * provider factory can observe produce different fingerprints and must not share.</p>
 *
 * <h3>Why this exists</h3>
 * <p>Provider instances are expensive — an HTTP client, or an ONNX inference session. Keying a pool
 * of them by namespace id would multiply that cost by the namespace count even when every namespace
 * is configured identically, which is the common case. Keying by configuration shares exactly where
 * sharing is correct.</p>
 *
 * <h3>Secret handling</h3>
 * <p>The API key participates through {@link #secretDigest()}, never in cleartext. Two accounts on
 * the same model have different quotas and rate limits and must not share a client, so the key has
 * to be part of the identity; but a fingerprint becomes a map key, a metric label and a log line,
 * so the raw value must not be. An absent or blank key yields {@link #NO_SECRET} rather than a
 * digest of the empty string, so "no credential configured" is legible rather than an opaque hash.</p>
 *
 * <h3>Equality</h3>
 * <p>This is a record over {@code String} and {@code int} components, so equality is exact — there
 * is no collision risk when it is used directly as a map key. {@link #digest()} is a compact
 * derived form for cache-key prefixes and metric labels, where a bounded-length token is needed.</p>
 *
 * @param name           provider instance name
 * @param type           provider type (e.g. {@code ollama}, {@code onnx}, {@code openai})
 * @param model          model identifier
 * @param baseUrl        API base URL, empty when not applicable
 * @param dimensions     configured embedding dimensionality, 0 for the model default
 * @param propertiesHash SHA-256 over the canonicalised provider-specific properties map
 * @param secretDigest   SHA-256 of the API key, or {@link #NO_SECRET} when none is configured
 */
public record ProviderFingerprint(
        String name,
        String type,
        String model,
        String baseUrl,
        int dimensions,
        String propertiesHash,
        String secretDigest
) {

    /** Sentinel {@link #secretDigest()} value used when no API key is configured. */
    public static final String NO_SECRET = "no-secret";

    /** Canonical {@link #propertiesHash()} value for an empty properties map. */
    public static final String NO_PROPERTIES = "no-properties";

    /** Number of hex characters in {@link #digest()} — 128 bits. */
    private static final int DIGEST_LENGTH = 32;

    /**
     * Validates components and normalises nulls, mirroring {@link ProviderConfig}'s compact
     * constructor so a fingerprint and the config it describes cannot disagree.
     */
    public ProviderFingerprint {
        Objects.requireNonNull(name, "Provider fingerprint name must not be null");
        Objects.requireNonNull(type, "Provider fingerprint type must not be null");
        if (dimensions < 0) {
            throw new IllegalArgumentException("Dimensions must be >= 0, got: " + dimensions);
        }
        model = model == null ? "" : model;
        baseUrl = baseUrl == null ? "" : baseUrl;
        propertiesHash = propertiesHash == null || propertiesHash.isBlank() ? NO_PROPERTIES : propertiesHash;
        secretDigest = secretDigest == null || secretDigest.isBlank() ? NO_SECRET : secretDigest;
    }

    /**
     * Derives the fingerprint of an effective provider configuration.
     *
     * <p>Every field a provider factory reads participates. Fields no factory reads are deliberately
     * excluded: including them would split the pool on a value that cannot change behaviour.</p>
     *
     * @param config the effective configuration, must not be {@code null}
     * @return the fingerprint
     */
    public static ProviderFingerprint of(ProviderConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return new ProviderFingerprint(
                config.name(),
                config.type(),
                config.model(),
                config.baseUrl(),
                config.dimensions(),
                hashProperties(config.properties()),
                digestSecret(config.apiKey())
        );
    }

    /**
     * Derives a fingerprint from a provider's own reported identity, for call sites that hold an
     * already-constructed {@link com.spectrayan.spector.provider.embedding.EmbeddingProvider} and no
     * {@link ProviderConfig} — the embedded path, where the provider is supplied directly rather than
     * built from configuration.
     *
     * <p>Weaker than {@link #of(ProviderConfig)}: it cannot distinguish two providers that report the
     * same model at the same dimensionality but differ in endpoint or credentials. It is nonetheless
     * sufficient for cache scoping, because two providers agreeing on model and dimensionality produce
     * interchangeable vectors. Use {@link #of(ProviderConfig)} wherever the configuration is available.</p>
     *
     * @param modelName  the provider's reported model name; {@code null} or blank becomes {@code "unknown"}
     * @param dimensions the provider's reported dimensionality
     * @return a fingerprint scoped to model identity
     */
    public static ProviderFingerprint ofModel(String modelName, int dimensions) {
        String model = modelName == null || modelName.isBlank() ? "unknown" : modelName;
        return new ProviderFingerprint(
                "supplied", "supplied", model, "", Math.max(dimensions, 0), NO_PROPERTIES, NO_SECRET);
    }

    /**
     * Derives a fingerprint by interrogating a provider instance, tolerating a provider that cannot
     * answer.
     *
     * <p>{@code modelName()} and {@code dimensions()} both reach the backend in some implementations
     * and can throw when it is unreachable. Fingerprinting must never be the reason a memory fails to
     * build — an offline embedding provider is a supported state
     * (see {@code SpectorAutoConfigurationTest.shouldGracefullyHandleOfflineEmbeddingProviderDuringDimensionProbing}),
     * so an unanswerable probe degrades to the weaker identity rather than propagating.</p>
     *
     * <p>Degrading is safe for cache scoping: a provider that cannot report its model gets the
     * {@code "unknown"} model scope, which is shared with other unreportable providers but never with
     * a provider that did identify itself. The cost of that sharing is a possible stale hit between two
     * anonymous providers; the cost of throwing would be a memory that will not open at all.</p>
     *
     * @param provider the provider to interrogate; {@code null} yields {@code null}
     * @return a fingerprint scoped to whatever identity the provider could supply, or {@code null}
     */
    public static ProviderFingerprint ofProvider(
            com.spectrayan.spector.provider.embedding.EmbeddingProvider provider) {
        if (provider == null) {
            return null;
        }
        String model;
        try {
            model = provider.modelName();
        } catch (RuntimeException e) {
            model = null;
        }
        int dimensions;
        try {
            dimensions = provider.dimensions();
        } catch (RuntimeException e) {
            dimensions = 0;
        }
        return ofModel(model, dimensions);
    }

    /**
     * Returns a compact, stable token derived from every component — 32 hex characters (128 bits).
     *
     * <p>Intended for embedding-cache key prefixes and metric labels, where an unbounded
     * {@link #toString()} is unsuitable. Carries no secret material.</p>
     *
     * @return a 32-character lowercase hex token
     */
    public String digest() {
        String canonical = String.join("\u0000",
                name, type, model, baseUrl, Integer.toString(dimensions), propertiesHash, secretDigest);
        return sha256Hex(canonical).substring(0, DIGEST_LENGTH);
    }

    /**
     * Returns a representation safe to log. Identical to {@link #toString()} because no component
     * carries secret material; provided so call sites read as deliberate rather than careless.
     *
     * @return a loggable description
     */
    public String toLogString() {
        return "ProviderFingerprint[type=" + type + ", model=" + model
                + ", dims=" + dimensions + ", digest=" + digest() + ']';
    }

    /**
     * Canonicalises a properties map into a stable hash.
     *
     * <p>Keys are sorted so that map iteration order cannot change the result, and both keys and
     * values are length-prefixed so that {@code {"ab":"c"}} and {@code {"a":"bc"}} cannot collide.</p>
     */
    private static String hashProperties(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return NO_PROPERTIES;
        }
        List<String> keys = new ArrayList<>(properties.keySet());
        keys.sort(String::compareTo);
        StringBuilder canonical = new StringBuilder();
        for (String key : keys) {
            String value = properties.get(key);
            String safeValue = value == null ? "" : value;
            canonical.append(key.length()).append(':').append(key)
                    .append('=').append(safeValue.length()).append(':').append(safeValue)
                    .append(';');
        }
        return sha256Hex(canonical.toString());
    }

    private static String digestSecret(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return NO_SECRET;
        }
        return sha256Hex(apiKey);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
