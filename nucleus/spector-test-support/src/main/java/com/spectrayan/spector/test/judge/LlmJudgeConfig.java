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
package com.spectrayan.spector.test.judge;

/**
 * Configuration for LLM test judge, loaded from environment variables and system properties.
 *
 * <h3>Environment Variables</h3>
 * <ul>
 *   <li>{@code LLM_JUDGE} — enable LLM judge (true/false, default: false)</li>
 *   <li>{@code LLM_JUDGE_MODEL} — model name (default: "qwen3:0.6b")</li>
 *   <li>{@code LLM_JUDGE_URL} — Ollama base URL (default: "http://localhost:11434")</li>
 *   <li>{@code LLM_JUDGE_FAIL_ON_REJECT} — hard-fail on not-relevant verdict (default: false)</li>
 *   <li>{@code LLM_JUDGE_CONFIDENCE} — minimum confidence threshold (default: 0.6)</li>
 * </ul>
 *
 * <p>System properties ({@code -DLLM_JUDGE=true}) take precedence over environment variables.</p>
 *
 * @param model              LLM model for judging
 * @param baseUrl            Ollama server URL
 * @param enabled            whether LLM judging is active
 * @param failOnReject       whether to hard-fail tests on NOT_RELEVANT verdict
 * @param confidenceThreshold minimum confidence for a RELEVANT verdict to pass
 */
public record LlmJudgeConfig(
        String model,
        String baseUrl,
        boolean enabled,
        boolean failOnReject,
        float confidenceThreshold
) {

    /** Default model for judging — use a model available locally. */
    public static final String DEFAULT_MODEL = "llama3.1";

    /** Default Ollama URL. */
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";

    /** Default confidence threshold. */
    public static final float DEFAULT_CONFIDENCE = 0.6f;

    /**
     * Loads configuration from environment variables and system properties.
     *
     * <p>System properties take precedence over environment variables.</p>
     *
     * @return resolved configuration
     */
    public static LlmJudgeConfig fromEnvironment() {
        boolean enabled = resolveBool("LLM_JUDGE", false);
        String model = resolveString("LLM_JUDGE_MODEL", DEFAULT_MODEL);
        String baseUrl = resolveString("LLM_JUDGE_URL", DEFAULT_BASE_URL);
        boolean failOnReject = resolveBool("LLM_JUDGE_FAIL_ON_REJECT", false);
        float confidence = resolveFloat("LLM_JUDGE_CONFIDENCE", DEFAULT_CONFIDENCE);

        return new LlmJudgeConfig(model, baseUrl, enabled, failOnReject, confidence);
    }

    /**
     * Creates a config for local testing with defaults.
     */
    public static LlmJudgeConfig localDefaults() {
        return new LlmJudgeConfig(DEFAULT_MODEL, DEFAULT_BASE_URL, true, false, DEFAULT_CONFIDENCE);
    }

    // ─────────────── Resolution helpers ───────────────

    private static String resolveString(String key, String defaultValue) {
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) return sysProp;

        String envVar = System.getenv(key);
        if (envVar != null && !envVar.isBlank()) return envVar;

        return defaultValue;
    }

    private static boolean resolveBool(String key, boolean defaultValue) {
        String value = resolveString(key, null);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private static float resolveFloat(String key, float defaultValue) {
        String value = resolveString(key, null);
        if (value == null) return defaultValue;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
