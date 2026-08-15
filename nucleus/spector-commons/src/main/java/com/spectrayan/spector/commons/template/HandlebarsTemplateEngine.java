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
package com.spectrayan.spector.commons.template;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, thread-safe Handlebars template engine implementation.
 *
 * <p>Features:
 * <ul>
 *   <li>Classpath resource resolution with customizable prefix/suffix</li>
 *   <li>No-op escaping strategy: raw Markdown and LLM prompt text is preserved without HTML entity replacement</li>
 *   <li>Thread-safe caching of compiled template ASTs</li>
 *   <li>Built-in formatting helpers: {@code formatDecimal}, {@code formatMult}, {@code truncate}, {@code default}, {@code join}, {@code padRight}, {@code upper}, {@code lower}</li>
 *   <li>Null-safe variable resolution</li>
 * </ul>
 * </p>
 */
public class HandlebarsTemplateEngine implements TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(HandlebarsTemplateEngine.class);

    private static final String DEFAULT_PREFIX = "/templates";
    private static final String DEFAULT_SUFFIX = ".hbs";

    private final Handlebars handlebars;
    private final Map<String, Template> inlineCache = new ConcurrentHashMap<>();

    /**
     * Constructs a {@code HandlebarsTemplateEngine} with default prefix ({@code /templates}) and suffix ({@code .hbs}).
     */
    public HandlebarsTemplateEngine() {
        this(DEFAULT_PREFIX, DEFAULT_SUFFIX);
    }

    /**
     * Constructs a {@code HandlebarsTemplateEngine} with a custom classpath prefix and suffix.
     *
     * @param prefix classpath resource prefix (e.g. {@code "/templates"})
     * @param suffix template file suffix (e.g. {@code ".hbs"})
     */
    public HandlebarsTemplateEngine(String prefix, String suffix) {
        String safePrefix = Objects.requireNonNull(prefix, "prefix cannot be null");
        String safeSuffix = Objects.requireNonNull(suffix, "suffix cannot be null");

        ClassPathTemplateLoader loader = new ClassPathTemplateLoader(safePrefix, safeSuffix);
        this.handlebars = new Handlebars(loader)
                .with(EscapingStrategy.NOOP)
                .prettyPrint(false);

        registerCustomHelpers();
        log.debug("[HandlebarsTemplateEngine] Initialized with prefix='{}', suffix='{}'", safePrefix, safeSuffix);
    }

    private void registerCustomHelpers() {
        // Formats floating point numbers: {{formatDecimal score "%.2f"}} or {{formatDecimal score}}
        handlebars.registerHelper("formatDecimal", (Object value, Options options) -> {
            if (value == null || value instanceof Map) return "0.00";
            String pattern = options.param(0, "%.2f");
            try {
                double num = value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString().trim());
                return String.format(pattern, num);
            } catch (Exception e) {
                return value.toString();
            }
        });

        // Formats multiplier scalars: {{formatMult multiplier}} -> "1.50×"
        handlebars.registerHelper("formatMult", (Object value, Options options) -> {
            if (value == null || value instanceof Map) return "1.00×";
            try {
                double num = value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString().trim());
                return String.format("%.2f×", num);
            } catch (Exception e) {
                return value.toString() + "×";
            }
        });

        // Truncates text with ellipsis: {{truncate text 200}} or {{truncate text}}
        handlebars.registerHelper("truncate", (Object value, Options options) -> {
            if (value == null || value instanceof Map) return "";
            String text = value.toString();
            int maxLen = options.param(0, 100);
            return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
        });

        // Fallback for null/empty values: {{default value "fallback"}}
        handlebars.registerHelper("default", (Object value, Options options) -> {
            if (value != null && !(value instanceof Map) && !value.toString().isBlank()) {
                return value.toString();
            }
            return options.param(0, "");
        });

        // Joins collections/arrays with a delimiter: {{join list ", "}}
        handlebars.registerHelper("join", (Object value, Options options) -> {
            if (value == null || value instanceof Map) return "";
            String delimiter = options.param(0, ", ");
            if (value instanceof Collection<?> coll) {
                return coll.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .reduce((a, b) -> a + delimiter + b)
                        .orElse("");
            } else if (value instanceof Object[] arr) {
                return java.util.Arrays.stream(arr)
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .reduce((a, b) -> a + delimiter + b)
                        .orElse("");
            } else if (value instanceof CharSequence cs) {
                return cs.toString();
            }
            return "";
        });

        // Pads a string to a specific width: {{padRight text 15}}
        handlebars.registerHelper("padRight", (Object value, Options options) -> {
            if (value == null || value instanceof Map) value = "";
            String str = value.toString();
            int width = options.param(0, 0);
            if (str.length() >= width) return str;
            return str + " ".repeat(width - str.length());
        });

        // Uppercases string: {{upper text}}
        handlebars.registerHelper("upper", (Object value, Options options) -> {
            if (value == null || value instanceof Map) return "";
            return value.toString().toUpperCase();
        });

        // Lowercases string: {{lower text}}
        handlebars.registerHelper("lower", (Object value, Options options) -> {
            if (value == null || value instanceof Map) return "";
            return value.toString().toLowerCase();
        });
    }

    @Override
    public String render(String templatePath, Object context) {
        Objects.requireNonNull(templatePath, "templatePath cannot be null");
        try {
            Template template = handlebars.compile(templatePath);
            return template.apply(context != null ? context : Map.of());
        } catch (IOException e) {
            log.error("[HandlebarsTemplateEngine] Failed to render template [{}]: {}", templatePath, e.getMessage());
            throw new IllegalArgumentException("Template rendering failed: " + templatePath + " (" + e.getMessage() + ")", e);
        }
    }

    @Override
    public String renderInline(String inlineTemplate, Object context) {
        Objects.requireNonNull(inlineTemplate, "inlineTemplate cannot be null");
        try {
            Template template = inlineCache.computeIfAbsent(inlineTemplate, key -> {
                try {
                    return handlebars.compileInline(key);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Invalid inline template syntax: " + e.getMessage(), e);
                }
            });
            return template.apply(context != null ? context : Map.of());
        } catch (Exception e) {
            log.error("[HandlebarsTemplateEngine] Failed to render inline template: {}", e.getMessage());
            throw new IllegalArgumentException("Inline template rendering failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean hasTemplate(String templatePath) {
        if (templatePath == null || templatePath.isBlank()) {
            return false;
        }
        try {
            handlebars.compile(templatePath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
