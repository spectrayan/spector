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

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Spring {@link ApplicationListener} that enforces account lockout by translating Spring
 * Security authentication events into {@link UserAccountStore} lockout-counter mutations
 * (Requirements 2.5, 13.1&ndash;13.5).
 *
 * <p>The listener is intentionally thin: all lockout policy (monotonic increment, locking at
 * {@code spector.auth.lockout.max-attempts}, never clearing/extending or incrementing while
 * already locked, and reset-on-success) lives in {@link UserAccountStore#recordFailure(String)}
 * and {@link UserAccountStore#recordSuccess(String)}. This class only maps an event's
 * {@link Authentication} to the owning user's 13-character TSID and delegates.</p>
 *
 * <p><strong>Failure events</strong> ({@link AbstractAuthenticationFailureEvent}, which includes
 * {@code AuthenticationFailureBadCredentialsEvent}) are published <em>before</em> a principal is
 * established, so the failed {@link Authentication#getName()} carries the <em>submitted login
 * handle</em>, not the TSID. The username is therefore resolved to a {@code user_id} via
 * {@link UserAccountStore#findByUsername(String)}; when it does not resolve to a known account the
 * event is a no-op (no enumeration side effects). {@code recordFailure} itself is monotonic while
 * locked, so failure events fired for an already-locked account (e.g. a {@code LockedException})
 * neither increment the counter nor extend the lock (Requirement 13.4).</p>
 *
 * <p><strong>Success events</strong> ({@link AuthenticationSuccessEvent}) are published after
 * {@code DaoAuthenticationProvider} has loaded the account, so the authenticated
 * {@link Authentication#getName()} is already the TSID {@code user_id} (see
 * {@code JdbcUserDetailsService}); it is passed straight to {@code recordSuccess}, which resets the
 * failure counter, clears the lock, and stamps {@code last_login_at} (Requirement 13.5).</p>
 */
@Component
public class LockoutEventListener implements ApplicationListener<AbstractAuthenticationEvent> {

    private static final Logger log = LoggerFactory.getLogger(LockoutEventListener.class);

    private final UserAccountStore accountStore;

    /**
     * Creates the listener.
     *
     * @param accountStore the JDBC-backed account store owning the lockout counters
     */
    public LockoutEventListener(UserAccountStore accountStore) {
        this.accountStore = Objects.requireNonNull(accountStore, "accountStore");
    }

    /**
     * Dispatches authentication events to the lockout counters.
     *
     * <p>Only {@link AbstractAuthenticationFailureEvent} and {@link AuthenticationSuccessEvent}
     * are acted upon; every other {@link AbstractAuthenticationEvent} is ignored.</p>
     *
     * @param event the published authentication event (never {@code null})
     */
    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        switch (event) {
            case AbstractAuthenticationFailureEvent failure -> handleFailure(failure);
            case AuthenticationSuccessEvent success -> handleSuccess(success);
            default -> { /* not a lockout-relevant event */ }
        }
    }

    /**
     * Handles an authentication failure by resolving the submitted login handle to a TSID and
     * recording a failed attempt. A no-op when the handle is blank or unknown.
     */
    private void handleFailure(AbstractAuthenticationFailureEvent failure) {
        String submittedUsername = nameOf(failure.getAuthentication());
        if (submittedUsername == null) {
            return;
        }
        Optional<UserRow> user = accountStore.findByUsername(submittedUsername);
        if (user.isEmpty()) {
            // Unknown login handle: nothing to lock. Avoid logging the raw handle (enumeration).
            log.debug("[Auth] Authentication failure for unknown login handle; no lockout update");
            return;
        }
        String userId = user.get().userId();
        accountStore.recordFailure(userId);
        log.debug("[Auth] Recorded authentication failure for user id={}", userId);
    }

    /**
     * Handles an authentication success by resetting the lockout counter for the authenticated
     * TSID principal.
     */
    private void handleSuccess(AuthenticationSuccessEvent success) {
        String userId = nameOf(success.getAuthentication());
        if (userId == null) {
            return;
        }
        // The success principal name is already the TSID (JdbcUserDetailsService.getUsername()).
        accountStore.recordSuccess(userId);
        log.debug("[Auth] Recorded authentication success for user id={}", userId);
    }

    /**
     * Extracts a non-blank principal name from an {@link Authentication}, or {@code null}.
     */
    private static String nameOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String name = authentication.getName();
        return (name == null || name.isBlank()) ? null : name;
    }
}
