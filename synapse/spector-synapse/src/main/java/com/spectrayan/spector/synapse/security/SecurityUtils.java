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
package com.spectrayan.spector.synapse.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the current principal and its authorities from Spring Security's
 * {@link SecurityContextHolder}.
 *
 * <p>Spector OSS is single-tenant, multi-user: the isolation boundary is the
 * individual authenticated user, identified by a 13-character TSID that is the
 * Spring Security principal name (never the login username). When no
 * non-anonymous {@link Authentication} is bound (auth disabled, or credentials
 * absent on a public path), the current user resolves to the literal
 * {@code "default"}, which maps to the single shared memory.
 *
 * <p>All methods are pure reads of the security context: they never mutate the
 * bound {@link Authentication}, initiate an authentication exchange, or alter
 * the security context state, and are therefore idempotent within an unchanged
 * context.
 */
public final class SecurityUtils {

    /** Literal user id used when the request is anonymous or auth is disabled. */
    private static final String DEFAULT_USER_ID = "default";

    /** Prefix Spring Security applies to scope authorities. */
    private static final String SCOPE_PREFIX = "SCOPE_";

    private SecurityUtils() {}

    /**
     * Resolves the current user id (TSID) from the bound {@link Authentication}.
     *
     * @return the non-anonymous principal name, or {@code "default"} when no
     *         non-anonymous {@link Authentication} is bound or the principal
     *         name is null/empty. Never {@code null}.
     */
    public static String getUserId() {
        Authentication auth = currentAuthentication();
        if (auth == null) {
            return DEFAULT_USER_ID;
        }
        String name = auth.getName();
        if (name == null || name.isEmpty()) {
            return DEFAULT_USER_ID;
        }
        return name;
    }

    /**
     * Returns the scope authorities granted to the current principal, with the
     * {@code SCOPE_} prefix stripped (e.g. {@code memory:read}).
     *
     * @return an immutable, insertion-ordered set of scopes; empty when the
     *         request is anonymous. Never {@code null}.
     */
    public static Set<String> getScopes() {
        Authentication auth = currentAuthentication();
        if (auth == null) {
            return Collections.emptySet();
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority == null) {
                continue;
            }
            String value = authority.getAuthority();
            if (value != null && value.startsWith(SCOPE_PREFIX)) {
                scopes.add(value.substring(SCOPE_PREFIX.length()));
            }
        }
        return Collections.unmodifiableSet(scopes);
    }

    /**
     * Tests whether the current principal holds the given scope.
     *
     * @param scope the scope name without the {@code SCOPE_} prefix
     *              (e.g. {@code memory:read})
     * @return {@code true} when the scope is present; {@code false} when the
     *         scope is {@code null} or the request is anonymous
     */
    public static boolean hasScope(String scope) {
        if (scope == null) {
            return false;
        }
        return getScopes().contains(scope);
    }

    /**
     * Indicates whether a non-anonymous {@link Authentication} is bound to the
     * current security context.
     *
     * @return {@code true} when a non-null, non-anonymous, authenticated
     *         {@link Authentication} is present; {@code false} otherwise
     */
    public static boolean isAuthenticated() {
        return currentAuthentication() != null;
    }

    /**
     * Retained for source compatibility only. Spector OSS is single-tenant, so
     * this always returns {@code "default"} and carries no isolation meaning.
     *
     * @return the literal {@code "default"}
     */
    public static String getTenantId() {
        return DEFAULT_USER_ID;
    }

    /**
     * Reads the current {@link Authentication} and normalizes anonymous states
     * to {@code null}.
     *
     * @return the bound {@link Authentication} when it is non-null, authenticated,
     *         and not an {@link AnonymousAuthenticationToken}; otherwise {@code null}
     */
    private static Authentication currentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }
}
