/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package org.spectrayan.testfixtures.synapse.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test fixture (not production code) exposing probe endpoints at the public, protected, MCP, and
 * scope-gated paths exercised by the security-config authorization gating test.
 *
 * <p>Deliberately lives OUTSIDE the {@code com.spectrayan} component-scan base package tree so that
 * {@code @SpringBootApplication} scanning in unrelated {@code @SpringBootTest} contexts never picks
 * up this {@code @RestController}.</p>
 */
@RestController
public class SecurityGatingProbeController {

    /** Authority required by the representative scope-gated route. */
    public static final String REQUIRED_SCOPE = "SCOPE_memory:write";

    @GetMapping("/actuator/health")
    String health() {
        return "health";
    }

    @GetMapping("/api/docs")
    String docs() {
        return "docs";
    }

    @GetMapping("/api/protected")
    String protectedEndpoint() {
        return "protected";
    }

    @GetMapping("/mcp")
    String mcp() {
        return "mcp";
    }

    @GetMapping("/api/scoped")
    @PreAuthorize("hasAuthority('SCOPE_memory:write')")
    String scoped() {
        return "scoped";
    }
}
