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
package com.spectrayan.spector.synapse.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class McpPinnedNamespaceTest {

    @Test
    void matchingSelectorBindsThePinnedSlug() {
        var decision = McpPinnedNamespace.decide(jwt("alpha-shared", null), "alpha-shared", true);
        assertThat(decision.error()).isNull();
        assertThat(decision.bindSelector()).isEqualTo("alpha-shared");
    }

    @Test
    void mismatchedSelectorIsRejected() {
        var decision = McpPinnedNamespace.decide(jwt("alpha-shared", null), "other", true);
        assertThat(decision.error()).contains("SPE-500-014");
        assertThat(decision.bindSelector()).isNull();
    }

    @Test
    void unpinnedTokenAndApiKeyAreRefused() {
        assertThat(McpPinnedNamespace.decide(jwt(null, null), null, true).error()).contains("SPE-110-008");
        var apiKey = new UsernamePasswordAuthenticationToken(
                "agent", "k", AuthorityUtils.createAuthorityList("ROLE_agent"));
        assertThat(McpPinnedNamespace.decide(apiKey, null, true).error()).contains("SPE-110-008");
    }

    @Test
    void authDisabledRejectsAnySelectorAndAllowsABareCall() {
        assertThat(McpPinnedNamespace.decide(null, "alpha", false).error()).contains("SPE-110-008");
        var bare = McpPinnedNamespace.decide(null, null, false);
        assertThat(bare.error()).isNull();
        assertThat(bare.bindSelector()).isNull();
    }

    private static JwtAuthenticationToken jwt(String slug, String nsid) {
        var builder = Jwt.withTokenValue("t").header("alg", "none").subject("agent");
        if (slug != null) {
            builder.claim("ns", List.of(slug));
        }
        if (nsid != null) {
            builder.claim("nsid", List.of(nsid));
        }
        return new JwtAuthenticationToken(builder.build(), AuthorityUtils.createAuthorityList("ROLE_agent"));
    }
}
