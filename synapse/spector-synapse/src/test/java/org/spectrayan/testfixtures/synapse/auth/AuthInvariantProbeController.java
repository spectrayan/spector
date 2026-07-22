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

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test probe controller for AuthInvariantProtectedPathsPropertyTest.
 */
@RestController
public class AuthInvariantProbeController {

    public static final AtomicInteger publicHits = new AtomicInteger();
    public static final AtomicInteger protectedHits = new AtomicInteger();
    public static final AtomicInteger scopedHits = new AtomicInteger();
    public static volatile boolean lastDownstreamAuthNonAnonymous = false;

    public static void reset() {
        publicHits.set(0);
        protectedHits.set(0);
        scopedHits.set(0);
        lastDownstreamAuthNonAnonymous = false;
    }

    public static int totalHits() {
        return publicHits.get() + protectedHits.get() + scopedHits.get();
    }

    private void recordAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        lastDownstreamAuthNonAnonymous = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }

    @GetMapping("/api/docs")
    public String publicEndpoint() {
        publicHits.incrementAndGet();
        recordAuth();
        return "public";
    }

    @GetMapping("/api/protected")
    public String protectedEndpoint() {
        protectedHits.incrementAndGet();
        recordAuth();
        return "protected";
    }

    @GetMapping("/api/scoped")
    @PreAuthorize("hasAuthority('SCOPE_memory:write')")
    public String scopedEndpoint() {
        scopedHits.incrementAndGet();
        recordAuth();
        return "scoped";
    }
}
