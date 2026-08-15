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

/**
 * Universal interface for compiling and rendering structured text, Markdown, 
 * and prompt templates across all Spector modules.
 *
 * <p>Implementations are thread-safe and provide sub-millisecond execution
 * via compiled AST caching.</p>
 */
public interface TemplateEngine {

    /**
     * Renders a named template located in the classpath (e.g., {@code "mcp/memory-status"}).
     *
     * @param templatePath classpath relative path without extension (relative to template root)
     * @param context      data model (Map, Record, Java bean, or POJO)
     * @return rendered output string
     */
    String render(String templatePath, Object context);

    /**
     * Renders an inline template string with the given context model.
     * Compiled ASTs are cached for high-throughput performance.
     *
     * @param inlineTemplate raw template string with Handlebars/Mustache syntax
     * @param context        data model
     * @return rendered output string
     */
    String renderInline(String inlineTemplate, Object context);

    /**
     * Checks if a template exists on the classpath at the given path.
     *
     * @param templatePath classpath relative path without extension
     * @return true if the template resource exists and compiles successfully
     */
    boolean hasTemplate(String templatePath);

    /**
     * Returns the shared singleton {@link TemplateEngine} instance 
     * with classpath root {@code /templates} and suffix {@code .hbs}.
     *
     * <p>This instance is thread-safe and reuses compiled AST caches across all callers.</p>
     *
     * @return default singleton template engine
     */
    static TemplateEngine getDefault() {
        return Holder.DEFAULT_INSTANCE;
    }

    /**
     * Returns the default singleton {@link TemplateEngine} instance.
     *
     * @return default singleton template engine
     */
    static TemplateEngine createDefault() {
        return Holder.DEFAULT_INSTANCE;
    }

    /**
     * Creates a new {@link HandlebarsTemplateEngine} with a custom classpath prefix and suffix.
     *
     * @param prefix classpath resource prefix (e.g. {@code "/templates"})
     * @param suffix template file suffix (e.g. {@code ".hbs"} or {@code ".mustache"})
     * @return customized template engine
     */
    static TemplateEngine create(String prefix, String suffix) {
        return new HandlebarsTemplateEngine(prefix, suffix);
    }

    /**
     * Lazy holder for the thread-safe singleton default engine.
     */
    final class Holder {
        private static final TemplateEngine DEFAULT_INSTANCE = new HandlebarsTemplateEngine();
        private Holder() {}
    }
}
