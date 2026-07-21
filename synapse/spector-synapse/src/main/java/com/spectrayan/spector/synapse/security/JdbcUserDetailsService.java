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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security {@link UserDetailsService} backed by the {@link UserAccountStore}
 * ({@code users} table). Provides the {@code UserDetails} that
 * {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider} verifies
 * against the {@link org.springframework.security.crypto.password.Pbkdf2PasswordEncoder}.
 *
 * <p>The returned principal name is the 13-character TSID {@code user_id} — never the login
 * {@code username} — so downstream identity resolution ({@code SecurityUtils.getUserId()}),
 * the JWT {@code sub} claim, and per-user filesystem paths all key off the stable TSID
 * (Requirements 7.1, 12.1). The exposed password is the stored {@code {pbkdf2}} hash; the raw
 * password is never read here (comparison is delegated to {@code DaoAuthenticationProvider}
 * + {@code Pbkdf2PasswordEncoder}).</p>
 *
 * <p>Authorities are the union of the account's roles (prefixed {@code ROLE_}) and scopes
 * (prefixed {@code SCOPE_}). {@code isEnabled()} mirrors the {@code active} column and
 * {@code isAccountNonLocked()} reflects the {@code locked_until} column relative to the
 * current instant.</p>
 */
@Service
public class JdbcUserDetailsService implements UserDetailsService {

    /** Prefix Spring Security uses for role authorities. */
    private static final String ROLE_PREFIX = "ROLE_";

    /** Prefix Spring Security uses for scope (OAuth2) authorities. */
    private static final String SCOPE_PREFIX = "SCOPE_";

    private final UserAccountStore accountStore;

    /**
     * Creates the service.
     *
     * @param accountStore JDBC-backed account store providing {@link UserRow} lookups
     */
    public JdbcUserDetailsService(UserAccountStore accountStore) {
        this.accountStore = accountStore;
    }

    /**
     * Loads a user by login handle and adapts it to a Spring Security {@link UserDetails}.
     *
     * <p>The returned {@code UserDetails} exposes the TSID {@code user_id} as its username
     * (the Spring Security principal name), the stored {@code {pbkdf2}} hash as its password,
     * {@code ROLE_*} + {@code SCOPE_*} authorities, {@code isEnabled() == active}, and
     * {@code isAccountNonLocked() == !isLocked(now)}.</p>
     *
     * @param username the login handle (case-insensitive; never the TSID)
     * @return a {@link UserDetails} keyed by the TSID principal
     * @throws UsernameNotFoundException when no user matches the handle
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        UserRow row = accountStore.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user for handle"));

        boolean locked = row.isLocked(Instant.now());

        return User.withUsername(row.userId())          // principal name == TSID, not the handle
                .password(row.passwordHash())            // stored {pbkdf2} hash, verified upstream
                .authorities(toAuthorities(row.roles(), row.scopes()))
                .disabled(!row.active())                 // isEnabled() maps active
                .accountLocked(locked)                   // isAccountNonLocked() maps locked_until
                .build();
    }

    /**
     * Builds the combined authority list: each role prefixed with {@code ROLE_} and each scope
     * prefixed with {@code SCOPE_}. Blank tokens are skipped.
     */
    private static List<GrantedAuthority> toAuthorities(Set<String> roles, Set<String> scopes) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            for (String role : roles) {
                if (role != null && !role.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.trim()));
                }
            }
        }
        if (scopes != null) {
            for (String scope : scopes) {
                if (scope != null && !scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + scope.trim()));
                }
            }
        }
        return authorities;
    }
}
