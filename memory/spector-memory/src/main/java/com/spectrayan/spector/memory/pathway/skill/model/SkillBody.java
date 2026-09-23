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
package com.spectrayan.spector.memory.pathway.skill.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-disk skill body representation combining YAML frontmatter metadata and markdown instruction body (ADR-0086 §5.4).
 *
 * <p>Supports safe fail-open parsing: any malformed frontmatter, missing closing fence, or legacy free-text string
 * safely returns the entire content as the body with a {@code null} metadata header.</p>
 */
public final class SkillBody {

    private static final Logger log = LoggerFactory.getLogger(SkillBody.class);
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^\\s*---\\r?\\n(.*?)\\r?\\n---\\r?\\n?(.*)$", Pattern.DOTALL);

    private final SkillMeta meta;
    private final String body;

    public SkillBody(final SkillMeta meta, final String body) {
        this.meta = meta;
        this.body = body != null ? body : "";
    }

    /**
     * Metadata frontmatter snapshot, or {@code null} if legacy or unparsed.
     */
    public SkillMeta meta() {
        return meta;
    }

    /**
     * Markdown body text following the frontmatter fence, or entire raw string if legacy.
     */
    public String body() {
        return body;
    }

    /**
     * Returns {@code true} if this skill has parsed metadata frontmatter.
     */
    public boolean hasMeta() {
        return meta != null;
    }

    /**
     * Parses a text blob into a {@link SkillBody} snapshot.
     *
     * @param content raw text content from TextBlobMemory
     * @return parsed SkillBody (fails open to entire string as body if unparsed)
     */
    public static SkillBody parse(final String content) {
        if (content == null || content.isEmpty()) {
            return new SkillBody(null, "");
        }

        // Strip UTF-8 BOM if present
        String text = content;
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }

        if (!text.stripLeading().startsWith("---")) {
            return new SkillBody(null, content);
        }

        Matcher matcher = FRONTMATTER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return new SkillBody(null, content);
        }

        String frontmatterYaml = matcher.group(1);
        String bodyText = matcher.group(2);

        try {
            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(options));
            Object loaded = yaml.load(frontmatterYaml);

            if (!(loaded instanceof Map<?, ?> rawMap)) {
                return new SkillBody(null, content);
            }

            String schema = extractString(rawMap.get("schema"));
            if (schema == null || !schema.startsWith("spector.skill.")) {
                log.debug("Unknown or missing skill schema: '{}', treating as legacy string", schema);
                return new SkillBody(null, content);
            }

            String name = extractString(rawMap.get("name"));
            SkillKind kind = SkillKind.from(extractString(rawMap.get("kind")));
            float confidence = extractFloat(rawMap.get("confidence"), 0.0f);
            List<String> tools = extractStringList(rawMap.get("tools"));
            Map<String, List<String>> parents = extractParentsMap(rawMap.get("parents"));

            SkillMeta meta = new SkillMeta(schema, name, kind, confidence, tools, parents);
            return new SkillBody(meta, bodyText.stripLeading());
        } catch (Exception e) {
            log.debug("Failed to parse skill frontmatter YAML, failing open: {}", e.getMessage());
            return new SkillBody(null, content);
        }
    }

    /**
     * Serializes this skill into the standard {@code spector.skill.v1} markdown format.
     *
     * @return serialized markdown string
     */
    public String serialize() {
        if (meta == null) {
            return body;
        }

        var sb = new StringBuilder();
        sb.append("---\n");
        sb.append("schema: ").append(meta.schema()).append("\n");
        if (meta.name() != null) {
            sb.append("name: ").append(meta.name()).append("\n");
        }
        sb.append("kind: ").append(meta.kind().name().toLowerCase()).append("\n");
        sb.append("confidence: ").append(String.format(java.util.Locale.ROOT, "%.2f", meta.confidence())).append("\n");

        if (meta.tools() != null && !meta.tools().isEmpty()) {
            sb.append("tools:\n");
            for (String tool : meta.tools()) {
                sb.append("  - ").append(tool).append("\n");
            }
        } else {
            sb.append("tools: []\n");
        }

        if (meta.parents() != null && !meta.parents().isEmpty()) {
            sb.append("parents:\n");
            for (Map.Entry<String, List<String>> entry : meta.parents().entrySet()) {
                sb.append("  ").append(entry.getKey()).append(":\n");
                for (String parentId : entry.getValue()) {
                    sb.append("    - \"").append(parentId).append("\"\n");
                }
            }
        } else {
            sb.append("parents: {}\n");
        }

        sb.append("---\n");
        sb.append(body);
        return sb.toString();
    }

    private static String extractString(final Object obj) {
        return obj instanceof String str ? str.trim() : null;
    }

    private static float extractFloat(final Object obj, final float defaultValue) {
        if (obj instanceof Number num) {
            return num.floatValue();
        }
        if (obj instanceof String str) {
            try {
                return Float.parseFloat(str.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(final Object obj) {
        if (!(obj instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item != null) {
                result.add(item.toString().trim());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> extractParentsMap(final Object obj) {
        if (!(obj instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().toString().trim() : null;
            if (key != null && entry.getValue() instanceof List<?> rawList) {
                List<String> items = new ArrayList<>();
                for (Object item : rawList) {
                    if (item != null) {
                        items.add(item.toString().trim());
                    }
                }
                result.put(key, items);
            }
        }
        return result;
    }
}
