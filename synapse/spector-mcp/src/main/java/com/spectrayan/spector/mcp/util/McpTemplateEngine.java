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
package com.spectrayan.spector.mcp.util;

import com.spectrayan.spector.commons.template.TemplateEngine;

/**
 * Singleton Handlebars template engine configured for MCP templates located at {@code /mcp/templates/*.hbs}.
 */
public final class McpTemplateEngine {

    private static final TemplateEngine ENGINE = TemplateEngine.create("/mcp/templates", ".hbs");

    private McpTemplateEngine() {}

    /**
     * Returns the shared {@link TemplateEngine} instance for MCP templates.
     *
     * @return template engine instance
     */
    public static TemplateEngine engine() {
        return ENGINE;
    }

    /**
     * Renders an MCP template with the given model.
     *
     * @param templateName name of the template relative to {@code /mcp/templates} (without {@code .hbs})
     * @param model        context object (Map, Record, or POJO)
     * @return rendered output string
     */
    public static String render(String templateName, Object model) {
        return ENGINE.render(templateName, model);
    }
}
