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

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.synapse.security.AgentTokenService;

/**
 * HTTP MCP namespace rule: the call runs in the namespace its token pins.
 * A namespace argument, header or query parameter may only repeat that pin.
 */
public final class McpPinnedNamespace {

    /**
     * @param error        formatted error when the call must not run, otherwise {@code null}
     * @param bindSelector namespace to hand the binder, or {@code null} when auth is off and no
     *                     selector was supplied
     */
    public record Decision(String error, String bindSelector) {
    }

    private McpPinnedNamespace() {
    }

    public static Decision decide(Authentication auth, String selector, boolean authEnabled) {
        String trimmed = (selector == null || selector.isBlank()) ? null : selector.trim();
        if (!authEnabled) {
            if (trimmed != null) {
                return new Decision(ErrorCode.MCP_TOKEN_NOT_PINNED.format(
                        "authentication is disabled, so a namespace selector is not accepted"), null);
            }
            return new Decision(null, null);
        }
        AgentTokenService.Pinned pin = pinOf(auth);
        if (pin == null) {
            return new Decision(ErrorCode.MCP_TOKEN_NOT_PINNED.format(
                    "HTTP MCP requires a token pinned to exactly one namespace"), null);
        }
        if (trimmed != null && !pin.matches(trimmed)) {
            return new Decision(ErrorCode.MCP_NAMESPACE_MISMATCH.format(trimmed, pin.describe()), null);
        }
        return new Decision(null, pin.bindSelector());
    }

    private static AgentTokenService.Pinned pinOf(Authentication auth) {
        Jwt jwt = jwtOf(auth);
        if (jwt == null) {
            return null;
        }
        List<String> ns = AgentTokenService.stringList(jwt.getClaim("ns"));
        List<String> nsid = AgentTokenService.stringList(jwt.getClaim("nsid"));
        AgentTokenService.Verified verified = new AgentTokenService.Verified(
                jwt.getSubject(), ns, nsid, jwt.getId(), jwt.getExpiresAt(),
                jwt.getClaimAsString("scope"), AgentTokenService.stringList(jwt.getClaim("roles")));
        try {
            return AgentTokenService.requirePinned(verified);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Jwt jwtOf(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        if (auth != null && auth.getPrincipal() instanceof Jwt principal) {
            return principal;
        }
        if (auth != null && auth.getCredentials() instanceof Jwt credentials) {
            return credentials;
        }
        return null;
    }
}
