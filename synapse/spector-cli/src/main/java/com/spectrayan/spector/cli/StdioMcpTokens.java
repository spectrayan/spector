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
package com.spectrayan.spector.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.mcp.McpCallGuard;
import com.spectrayan.spector.synapse.security.AgentTokenService;

/**
 * Loads {@code SPECTOR_TOKEN_FILE} for the stdio MCP server and re-reads it near expiry.
 */
public final class StdioMcpTokens {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpTokens.class);

    private StdioMcpTokens() {
    }

    public static AgentTokenService.Pinned load() {
        String tokenPath = System.getenv("SPECTOR_TOKEN_FILE");
        if (tokenPath == null || tokenPath.isBlank()) {
            throw new SpectorConfigException(ErrorCode.MCP_TOKEN_REQUIRED, "SPECTOR_TOKEN_FILE is not set");
        }
        Path tokenFile = Path.of(tokenPath);
        if (!Files.isRegularFile(tokenFile)) {
            throw new SpectorConfigException(ErrorCode.MCP_TOKEN_REQUIRED,
                    "SPECTOR_TOKEN_FILE does not exist: " + tokenPath);
        }
        Path jwks = jwksFile();
        return AgentTokenService.requirePinned(AgentTokenService.verify(tokenFile, jwks));
    }

    /**
     * Installs the per-call guard. A replacement token must keep the same subject and namespace.
     */
    public static void install(AgentTokenService.Pinned initial) {
        String tokenPath = System.getenv("SPECTOR_TOKEN_FILE");
        Path tokenFile = Path.of(tokenPath);
        Path jwks = jwksFile();
        Holder holder = new Holder(initial);
        log.info("[Spector MCP] bound namespace '{}' for sub={}, token exp={}",
                initial.describe(), initial.verified().subject(), initial.verified().expiresAt());
        McpCallGuard.install(args -> holder.reject(tokenFile, jwks, args));
    }

    static Path jwksFile() {
        String path = System.getenv("SPECTOR_AUTH_AGENT_JWKS_FILE");
        if (path == null || path.isBlank()) {
            path = System.getProperty("spector.auth.agent-jwks-file");
        }
        if (path == null || path.isBlank()) {
            throw new SpectorConfigException(ErrorCode.JWT_KEY_INVALID,
                    "SPECTOR_AUTH_AGENT_JWKS_FILE", "not set");
        }
        return Path.of(path);
    }

    private static final class Holder {
        private AgentTokenService.Pinned current;

        private Holder(AgentTokenService.Pinned current) {
            this.current = current;
        }

        private String reject(Path tokenFile, Path jwks, Map<String, Object> args) {
            try {
                if (!current.verified().expiresAt().minus(AgentTokenService.ROTATE_WITHIN).isAfter(Instant.now())) {
                    AgentTokenService.Pinned next = AgentTokenService.requirePinned(
                            AgentTokenService.verify(tokenFile, jwks));
                    if (!next.verified().subject().equals(current.verified().subject())
                            || !next.describe().equals(current.describe())) {
                        return ErrorCode.AGENT_TOKEN_INVALID.format(
                                "replacement token changed subject or namespace");
                    }
                    current = next;
                    log.info("[Spector MCP] reloaded token jti={} exp={}", next.verified().jti(),
                            next.verified().expiresAt());
                }
            } catch (SpectorConfigException e) {
                return e.getMessage();
            }
            if (current.verified().expiresAt().plus(AgentTokenService.CLOCK_SKEW).isBefore(Instant.now())) {
                return ErrorCode.AGENT_TOKEN_INVALID.format("expired; run spector token issue");
            }
            if (args != null && args.get("namespace") != null) {
                String selector = String.valueOf(args.get("namespace")).trim();
                if (!selector.isEmpty() && !current.matches(selector)) {
                    return ErrorCode.MCP_NAMESPACE_MISMATCH.format(selector, current.describe());
                }
            }
            return null;
        }
    }

    /** Test hook: the token file path is supplied by the caller rather than the environment. */
    public static String rejectForTest(AgentTokenService.Pinned pinned, Map<String, Object> args) {
        if (args != null && args.get("namespace") != null) {
            String selector = String.valueOf(args.get("namespace")).trim();
            if (!selector.isEmpty() && !pinned.matches(selector)) {
                return ErrorCode.MCP_NAMESPACE_MISMATCH.format(selector, pinned.describe());
            }
        }
        return null;
    }

    static void touch(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
    }
}
