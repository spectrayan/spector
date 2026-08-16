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
package com.spectrayan.spector.synapse.ratelimit;

import com.spectrayan.spector.config.properties.RateLimitProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Resolves caller identity and service tier from HTTP request headers and security context.
 */
public class RateLimitKeyResolver {

    private final SynapseProperties properties;

    public RateLimitKeyResolver(SynapseProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves the {@link RateLimitKey} using prioritized identity extraction.
     *
     * @param request current HTTP servlet request
     * @return resolved rate limit key and tier
     */
    public RateLimitKey resolveKey(HttpServletRequest request) {
        // 1. API Key Header (X-API-Key)
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("ApiKey ")) {
                apiKey = authHeader.substring(7).trim();
            }
        }

        if (apiKey != null && !apiKey.isBlank()) {
            String hashed = hashKey(apiKey);
            String configuredSystemKey = properties != null ? properties.getApiKey() : null;
            String tier = (configuredSystemKey != null && configuredSystemKey.equals(apiKey)) ? "system" : "standard";
            return new RateLimitKey(RateLimitKey.KeyType.API_KEY, hashed, tier);
        }

        // 2. Spring Security Authentication Principal
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            String name = auth.getName();
            String tier = "standard";
            for (GrantedAuthority authority : auth.getAuthorities()) {
                String role = authority.getAuthority();
                if ("ROLE_ADMIN".equalsIgnoreCase(role) || "ROLE_SYSTEM".equalsIgnoreCase(role)) {
                    tier = "system";
                    break;
                } else if ("ROLE_PREMIUM".equalsIgnoreCase(role)) {
                    tier = "premium";
                }
            }
            return new RateLimitKey(RateLimitKey.KeyType.USER, name, tier);
        }

        // 3. Tenant Header (X-Tenant-ID)
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId != null && !tenantId.isBlank()) {
            return new RateLimitKey(RateLimitKey.KeyType.TENANT, tenantId.trim(), "standard");
        }

        // 4. Client IP
        String clientIp = extractClientIp(request);
        return new RateLimitKey(RateLimitKey.KeyType.IP, clientIp, "anonymous");
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0 && !ips[0].trim().isBlank()) {
                return ips[0].trim();
            }
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }

    private String hashKey(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, Math.min(hash.length, 16));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
