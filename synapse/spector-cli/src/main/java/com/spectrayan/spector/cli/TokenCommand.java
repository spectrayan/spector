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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.synapse.security.AgentTokenService;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Issues and inspects ES256 agent tokens. These commands do not open a memory store.
 */
@Component
@Command(
        name = "token",
        description = "Create an ES256 agent key pair, issue a namespace-pinned token, or inspect one.",
        mixinStandardHelpOptions = true,
        subcommands = {
                TokenCommand.Keygen.class,
                TokenCommand.Issue.class,
                TokenCommand.Inspect.class
        }
)
public class TokenCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Command(name = "keygen", description = "Write an ES256 private key (mode 0600) and a public JWKS file.",
            mixinStandardHelpOptions = true)
    public static class Keygen implements Runnable {
        @Option(names = "--private-key", required = true, description = "Private key file to create (mode 0600).")
        Path privateKey;

        @Option(names = "--jwks", required = true, description = "Public JWKS file to create.")
        Path jwks;

        @Spec
        CommandSpec spec;

        @Override
        public void run() {
            AgentTokenService.KeyMaterial material = AgentTokenService.keygen(privateKey, jwks);
            spec.commandLine().getOut().println("kid=" + material.keyId());
            spec.commandLine().getOut().println("private-key=" + material.privateKeyFile());
            spec.commandLine().getOut().println("jwks=" + material.jwksFile());
        }
    }

    @Command(name = "issue",
            description = "Write a namespace-pinned agent token to a 0600 file. The token is not printed.",
            mixinStandardHelpOptions = true)
    public static class Issue implements Runnable {
        @Option(names = "--agent", required = true, description = "Agent subject (sub).")
        String agent;

        @Option(names = "--namespace", required = true, description = "Single namespace slug the token is pinned to.")
        String namespace;

        @Option(names = "--private-key", required = true, description = "ES256 private key from `spector token keygen`.")
        Path privateKey;

        @Option(names = "--out", required = true, description = "Token file to write (mode 0600).")
        Path out;

        @Option(names = "--ttl", description = "Lifetime (default 30d, maximum 90d). Examples: 30d, 12h.")
        String ttl;

        @Option(names = "--scopes", description = "Comma-separated scopes. Default: spector:memory:read,spector:memory:write.")
        String scopes;

        @Option(names = "--roles", description = "Comma-separated roles. Default: agent.")
        String roles;

        @Spec
        CommandSpec spec;

        @Override
        public void run() {
            AgentTokenService.Issued issued = AgentTokenService.issue(
                    privateKey, agent, namespace, parseTtl(ttl), split(scopes), split(roles), out);
            var outWriter = spec.commandLine().getOut();
            outWriter.println("sub=" + issued.subject());
            outWriter.println("ns=" + issued.namespace());
            outWriter.println("jti=" + issued.jti());
            outWriter.println("exp=" + issued.expiresAt());
            outWriter.println("file=" + issued.file());
        }
    }

    @Command(name = "inspect", description = "Verify a token file and print its claims. The token itself is not printed.",
            mixinStandardHelpOptions = true)
    public static class Inspect implements Runnable {
        @Option(names = "--file", required = true, description = "Token file to verify.")
        Path file;

        @Option(names = "--jwks", required = true, description = "Public JWKS used to verify the token.")
        Path jwks;

        @Spec
        CommandSpec spec;

        @Override
        public void run() {
            AgentTokenService.Verified verified = AgentTokenService.verify(file, jwks);
            boolean pinned = false;
            String pin = "";
            try {
                AgentTokenService.Pinned p = AgentTokenService.requirePinned(verified);
                pinned = true;
                pin = p.describe();
            } catch (SpectorConfigException ignored) {
                pinned = false;
            }
            var out = spec.commandLine().getOut();
            out.println("sub=" + verified.subject());
            out.println("ns=" + verified.namespaces());
            out.println("nsid=" + verified.namespaceIds());
            out.println("pinned=" + pinned);
            if (pinned) {
                out.println("namespace=" + pin);
            }
            out.println("jti=" + verified.jti());
            out.println("exp=" + verified.expiresAt());
            out.println("scope=" + verified.scope());
            out.println("roles=" + verified.roles());
        }
    }

    static Duration parseTtl(String raw) {
        if (raw == null || raw.isBlank()) {
            return AgentTokenService.DEFAULT_TTL;
        }
        String value = raw.trim().toLowerCase();
        try {
            if (value.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            return Duration.parse(value.startsWith("p") ? value.toUpperCase() : value);
        } catch (RuntimeException e) {
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "--ttl", raw);
        }
    }

    static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Visible for tests. */
    public static boolean isOwnerOnly(Path path) throws Exception {
        if (!Files.exists(path)) {
            return false;
        }
        try {
            var perms = Files.getPosixFilePermissions(path);
            return perms.equals(java.util.EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }
}
