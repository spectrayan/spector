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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0086 §5.4: SkillBody Frontmatter Parser Tests")
class SkillBodyParseTest {

    @Test
    @DisplayName("Legacy free-text string without YAML fence fails open cleanly")
    void testLegacyProseFailsOpen() {
        String legacyText = "PROCEDURAL RULE: When a login endpoint throws NPE on missing principal, guard in validator.";
        SkillBody body = SkillBody.parse(legacyText);

        assertThat(body.hasMeta()).isFalse();
        assertThat(body.meta()).isNull();
        assertThat(body.body()).isEqualTo(legacyText);
    }

    @Test
    @DisplayName("Valid spector.skill.v1 frontmatter parses into SkillMeta and body")
    void testValidV1SkillParsing() {
        String v1Content = """
                ---
                schema: spector.skill.v1
                name: null-check-auth-validator
                kind: playbook
                confidence: 0.35
                tools:
                  - auth_tool
                parents:
                  episodic:
                    - "07ABC123XYZ"
                  semantic:
                    - "rem-log-999"
                ---
                # Null-check auth validator

                When a login endpoint throws NPE on missing principal:
                1. Reproduce
                2. Guard principal
                """;

        SkillBody body = SkillBody.parse(v1Content);

        assertThat(body.hasMeta()).isTrue();
        SkillMeta meta = body.meta();
        assertThat(meta).isNotNull();
        assertThat(meta.schema()).isEqualTo("spector.skill.v1");
        assertThat(meta.name()).isEqualTo("null-check-auth-validator");
        assertThat(meta.kind()).isEqualTo(SkillKind.PLAYBOOK);
        assertThat(meta.confidence()).isEqualTo(0.35f);
        assertThat(meta.tools()).containsExactly("auth_tool");
        assertThat(meta.parents()).containsKeys("episodic", "semantic");
        assertThat(meta.parents().get("episodic")).containsExactly("07ABC123XYZ");
        assertThat(meta.parents().get("semantic")).containsExactly("rem-log-999");

        assertThat(body.body()).startsWith("# Null-check auth validator");
    }

    @Test
    @DisplayName("Corrupt YAML syntax fails open to entire string as body without throwing")
    void testCorruptYamlFailsOpen() {
        String corruptContent = """
                ---
                schema: spector.skill.v1
                name: [unclosed list
                kind: : : invalid yaml syntax
                ---
                Some markdown text here.
                """;

        SkillBody body = SkillBody.parse(corruptContent);

        assertThat(body.hasMeta()).isFalse();
        assertThat(body.meta()).isNull();
        assertThat(body.body()).isEqualTo(corruptContent);
    }

    @Test
    @DisplayName("Unknown schema fails open to entire string")
    void testUnknownSchemaFailsOpen() {
        String unknownSchema = """
                ---
                schema: custom.foreign.format.v9
                name: test
                ---
                Markdown body
                """;

        SkillBody body = SkillBody.parse(unknownSchema);

        assertThat(body.hasMeta()).isFalse();
        assertThat(body.meta()).isNull();
        assertThat(body.body()).isEqualTo(unknownSchema);
    }

    @Test
    @DisplayName("UTF-8 BOM is stripped cleanly before checking frontmatter")
    void testBomStripped() {
        String withBom = "\uFEFF---\nschema: spector.skill.v1\nname: bom-test\nkind: heuristic\n---\nBody with BOM";
        SkillBody body = SkillBody.parse(withBom);

        assertThat(body.hasMeta()).isTrue();
        assertThat(body.meta().name()).isEqualTo("bom-test");
        assertThat(body.meta().kind()).isEqualTo(SkillKind.HEURISTIC);
        assertThat(body.body()).isEqualTo("Body with BOM");
    }

    @Test
    @DisplayName("Roundtrip serialization produces valid v1 formatted markdown")
    void testSerializationRoundtrip() {
        SkillMeta meta = new SkillMeta(
                SkillMeta.SCHEMA_V1,
                "login-guard",
                SkillKind.PLAYBOOK,
                0.40f,
                List.of("guard_tool"),
                java.util.Map.of("episodic", List.of("epi-1", "epi-2"))
        );
        SkillBody original = new SkillBody(meta, "# Header\nAction sequence.");
        String serialized = original.serialize();

        SkillBody parsed = SkillBody.parse(serialized);
        assertThat(parsed.hasMeta()).isTrue();
        assertThat(parsed.meta().name()).isEqualTo("login-guard");
        assertThat(parsed.meta().kind()).isEqualTo(SkillKind.PLAYBOOK);
        assertThat(parsed.meta().confidence()).isEqualTo(0.40f);
        assertThat(parsed.meta().tools()).containsExactly("guard_tool");
        assertThat(parsed.meta().parents().get("episodic")).containsExactly("epi-1", "epi-2");
        assertThat(parsed.body()).isEqualTo("# Header\nAction sequence.");
    }
}
