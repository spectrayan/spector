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
package com.spectrayan.spector.synapse.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;

class AgentTokenServiceTest {

    @TempDir
    Path dir;

    @Test
    void keygenIssueAndInspectRoundTrip() throws Exception {
        Path key = dir.resolve("agent.key");
        Path jwks = dir.resolve("agent.jwks");
        Path token = dir.resolve("agent.jwt");
        AgentTokenService.keygen(key, jwks);
        assertThat(Files.getPosixFilePermissions(key))
                .isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

        AgentTokenService.Issued issued = AgentTokenService.issue(
                key, "grokbot", "grokbot", Duration.ofDays(30), null, null, token);
        assertThat(issued.namespace()).isEqualTo("grokbot");
        assertThat(Files.getPosixFilePermissions(token))
                .isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        String compact = Files.readString(token);
        assertThat(issued.toString()).doesNotContain(compact);

        AgentTokenService.Pinned pinned = AgentTokenService.requirePinned(AgentTokenService.verify(token, jwks));
        assertThat(pinned.verified().subject()).isEqualTo("grokbot");
        assertThat(pinned.slug()).isEqualTo("grokbot");
        assertThat(pinned.matches("grokbot")).isTrue();
        assertThat(pinned.matches("other")).isFalse();
    }

    @Test
    void ttlAboveNinetyDaysIsRejected() {
        Path key = dir.resolve("agent.key");
        Path jwks = dir.resolve("agent.jwks");
        AgentTokenService.keygen(key, jwks);
        assertThatThrownBy(() -> AgentTokenService.issue(
                key, "grokbot", "grokbot", Duration.ofDays(91), null, null, dir.resolve("t.jwt")))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("SPE-110-003");
    }

    @Test
    void noneAlgorithmIsRejected() throws Exception {
        Path jwks = dir.resolve("agent.jwks");
        AgentTokenService.keygen(dir.resolve("agent.key"), jwks);
        Path token = dir.resolve("none.jwt");
        String none = "eyJhbGciOiJub25lIn0.eyJpc3MiOiJzcGVjdG9yLWFnZW50Iiwic3ViIjoieCJ9.";
        Files.writeString(token, none);
        assertThatThrownBy(() -> AgentTokenService.verify(token, jwks))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining(ErrorCode.AGENT_TOKEN_INVALID.id());
    }

    @Test
    void twoNamespacesAreNotPinned() throws Exception {
        Path key = dir.resolve("agent.key");
        Path jwks = dir.resolve("agent.jwks");
        Path token = dir.resolve("agent.jwt");
        AgentTokenService.keygen(key, jwks);
        AgentTokenService.issue(key, "grokbot", "grokbot", null, null, null, token);
        AgentTokenService.Verified verified = AgentTokenService.verify(token, jwks);
        AgentTokenService.Verified unpinned = new AgentTokenService.Verified(
                verified.subject(), java.util.List.of("a", "b"), java.util.List.of(),
                verified.jti(), verified.expiresAt(), verified.scope(), verified.roles());
        assertThatThrownBy(() -> AgentTokenService.requirePinned(unpinned))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("SPE-110-008");
    }
}
