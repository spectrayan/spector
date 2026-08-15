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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TemplateEngine & HandlebarsTemplateEngine Tests")
class TemplateEngineTest {

    private TemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = TemplateEngine.createDefault();
    }

    @Nested
    @DisplayName("Classpath Template Rendering")
    class ClasspathRenderingTests {

        @Test
        @DisplayName("renders classpath template with map context and custom helpers")
        void rendersClasspathTemplate() {
            var model = Map.of(
                    "name", "Alice",
                    "platform", "Spector",
                    "admin", true,
                    "score", 9.8765,
                    "boost", 1.5,
                    "bio", "Principal AI Engineer specializing in memory architectures",
                    "tags", List.of("memory", "cognitive", "ai")
            );

            String result = engine.render("test-greeting", model);

            assertThat(result)
                    .contains("Hello, Alice! Welcome to Spector.")
                    .contains("Role: Administrator")
                    .contains("Score: 9.88")
                    .contains("Multiplier: 1.50×")
                    .contains("Bio: Principal AI En…")
                    .contains("Fallback: DefaultValue")
                    .contains("Tags: memory, cognitive, ai");
        }

        @Test
        @DisplayName("renders classpath template with record model")
        void rendersWithRecordModel() {
            record UserContext(String name, String platform, boolean admin, double score, double boost, String bio, List<String> tags) {}

            var user = new UserContext(
                    "Bob", "Synapse", false, 4.321, 0.85, "Short bio", List.of("dev", "test")
            );

            String result = engine.render("test-greeting", user);

            assertThat(result)
                    .contains("Hello, Bob! Welcome to Synapse.")
                    .contains("Role: Standard User")
                    .contains("Score: 4.32")
                    .contains("Multiplier: 0.85×")
                    .contains("Bio: Short bio")
                    .contains("Tags: dev, test");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when template does not exist")
        void throwsOnMissingTemplate() {
            assertThatThrownBy(() -> engine.render("non-existent-template", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Template rendering failed: non-existent-template");
        }
    }

    @Nested
    @DisplayName("Inline Template Rendering")
    class InlineRenderingTests {

        @Test
        @DisplayName("renders inline template string with conditionals and loops")
        void rendersInlineTemplate() {
            String template = """
                    # {{title}}
                    {{#if showSummary}}
                    Summary: {{summary}}
                    {{/if}}
                    Items:
                    {{#each items}}
                    - [{{id}}] {{name}} (score: {{formatDecimal score "%.1f"}})
                    {{/each}}
                    """;

            var model = Map.of(
                    "title", "Search Results",
                    "showSummary", true,
                    "summary", "Found 2 matching items",
                    "items", List.of(
                            Map.of("id", "M1", "name", "Memory 1", "score", 0.95),
                            Map.of("id", "M2", "name", "Memory 2", "score", 0.72)
                    )
            );

            String result = engine.renderInline(template, model);

            assertThat(result)
                    .contains("# Search Results")
                    .contains("Summary: Found 2 matching items")
                    .contains("- [M1] Memory 1 (score: 1.0)")
                    .contains("- [M2] Memory 2 (score: 0.7)");
        }

        @Test
        @DisplayName("preserves raw Markdown characters without HTML escaping")
        void preservesMarkdownCharacters() {
            String template = "Formula: {{formula}} | Query: <{{query}}> & \"{{tag}}\"";
            var model = Map.of(
                    "formula", "x < y && z > w",
                    "query", "vector > 0.85",
                    "tag", "ai & memory"
            );

            String result = engine.renderInline(template, model);

            assertThat(result)
                    .isEqualTo("Formula: x < y && z > w | Query: <vector > 0.85> & \"ai & memory\"")
                    .doesNotContain("&lt;")
                    .doesNotContain("&gt;")
                    .doesNotContain("&amp;")
                    .doesNotContain("&quot;");
        }

        @Test
        @DisplayName("handles null context gracefully")
        void handlesNullContext() {
            String result = engine.renderInline("Constant template without variables", null);
            assertThat(result).isEqualTo("Constant template without variables");
        }

        @Test
        @DisplayName("throws IllegalArgumentException on syntax error in inline template")
        void throwsOnInvalidSyntax() {
            assertThatThrownBy(() -> engine.renderInline("Hello {{#unclosed", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Custom Helpers")
    class CustomHelpersTests {

        @Test
        @DisplayName("formatDecimal formats numbers and handles nulls/strings safely")
        void testFormatDecimal() {
            assertThat(engine.renderInline("{{formatDecimal val}}", Map.of("val", 3.14159)))
                    .isEqualTo("3.14");
            assertThat(engine.renderInline("{{formatDecimal val \"%.4f\"}}", Map.of("val", 3.14159)))
                    .isEqualTo("3.1416");
            assertThat(engine.renderInline("{{formatDecimal val}}", Map.of()))
                    .isEqualTo("0.00");
            assertThat(engine.renderInline("{{formatDecimal val \"%.1f\"}}", Map.of("val", "42.87")))
                    .isEqualTo("42.9");
        }

        @Test
        @DisplayName("formatMult formats multiplier scalar with × suffix")
        void testFormatMult() {
            assertThat(engine.renderInline("{{formatMult val}}", Map.of("val", 1.5)))
                    .isEqualTo("1.50×");
            assertThat(engine.renderInline("{{formatMult val}}", Map.of()))
                    .isEqualTo("1.00×");
        }

        @Test
        @DisplayName("truncate trims text and appends ellipsis")
        void testTruncate() {
            assertThat(engine.renderInline("{{truncate text 10}}", Map.of("text", "Short")))
                    .isEqualTo("Short");
            assertThat(engine.renderInline("{{truncate text 10}}", Map.of("text", "This is very long text")))
                    .isEqualTo("This is ve…");
            assertThat(engine.renderInline("{{truncate text 10}}", Map.of()))
                    .isEqualTo("");
        }

        @Test
        @DisplayName("default helper returns value if present or fallback if null/empty")
        void testDefaultHelper() {
            assertThat(engine.renderInline("{{default val \"fallback\"}}", Map.of("val", "Actual")))
                    .isEqualTo("Actual");
            assertThat(engine.renderInline("{{default val \"fallback\"}}", Map.of("val", "")))
                    .isEqualTo("fallback");
            assertThat(engine.renderInline("{{default val \"fallback\"}}", Map.of()))
                    .isEqualTo("fallback");
        }

        @Test
        @DisplayName("join helper combines collections and arrays with delimiter")
        void testJoinHelper() {
            assertThat(engine.renderInline("{{join list \", \"}}", Map.of("list", List.of("A", "B", "C"))))
                    .isEqualTo("A, B, C");
            assertThat(engine.renderInline("{{join arr \" | \"}}", Map.of("arr", new String[]{"X", "Y"})))
                    .isEqualTo("X | Y");
            assertThat(engine.renderInline("{{join empty \", \"}}", Map.of()))
                    .isEqualTo("");
        }

        @Test
        @DisplayName("padRight helper pads strings to desired width")
        void testPadRight() {
            assertThat(engine.renderInline("{{padRight text 10}}.", Map.of("text", "Hello")))
                    .isEqualTo("Hello     .");
            assertThat(engine.renderInline("{{padRight text 4}}.", Map.of("text", "LongerText")))
                    .isEqualTo("LongerText.");
        }

        @Test
        @DisplayName("upper and lower helpers transform string case")
        void testCaseHelpers() {
            assertThat(engine.renderInline("{{upper text}}", Map.of("text", "hello world")))
                    .isEqualTo("HELLO WORLD");
            assertThat(engine.renderInline("{{lower text}}", Map.of("text", "HELLO WORLD")))
                    .isEqualTo("hello world");
        }
    }

    @Nested
    @DisplayName("Template Existence & Lifecycle")
    class TemplateExistenceTests {

        @Test
        @DisplayName("hasTemplate returns true for existing templates and false for missing")
        void testHasTemplate() {
            assertThat(engine.hasTemplate("test-greeting")).isTrue();
            assertThat(engine.hasTemplate("missing-template")).isFalse();
            assertThat(engine.hasTemplate(null)).isFalse();
            assertThat(engine.hasTemplate("")).isFalse();
        }

        @Test
        @DisplayName("create factory allows custom prefix and suffix")
        void testCustomPrefixSuffix() {
            TemplateEngine customEngine = TemplateEngine.create("/custom", ".mustache");
            assertThat(customEngine).isNotNull();
            assertThat(customEngine.hasTemplate("greeting")).isFalse();
        }
    }

    @Nested
    @DisplayName("Thread Safety & Concurrency")
    class ConcurrencyTests {

        @Test
        @DisplayName("renders concurrently across 50 virtual threads without race conditions")
        void testConcurrentRendering() throws InterruptedException {
            int threadCount = 50;
            int iterationsPerThread = 100;
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            var latch = new CountDownLatch(threadCount);
            var successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < iterationsPerThread; j++) {
                            String result = engine.renderInline("Thread {{id}} - Iteration {{iter}}: {{formatDecimal score \"%.2f\"}}",
                                    Map.of("id", threadId, "iter", j, "score", threadId + j * 0.1));
                            if (result.startsWith("Thread " + threadId)) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(threadCount * iterationsPerThread);
        }
    }
}
