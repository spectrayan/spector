/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.commons.pathway.DefaultPathwayCatalog;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.Pathway;
import com.spectrayan.spector.commons.pathway.PathwayContext;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.model.RememberResult;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight stand-in for {@link RememberPathway}, registered in a
 * {@link DefaultPathwayCatalog} under the {@code RememberPathway.class} key.
 *
 * <p>This is the fake ADR-0035 §14 calls for: it lets Dream and Reflect tests drive nested
 * ingestion <em>without constructing any Remember relay</em>, which is the whole point of
 * routing cross-pathway calls through the catalog. Registering a fake under the real key is
 * legal because {@code PathwayCatalog.register} is typed on
 * {@code Pathway<I, O>}, not on the concrete pathway class.</p>
 *
 * <p>Prefer this over {@code Mockito.mock(RememberPathway.class)} for nested-invocation
 * tests: a Mockito mock answers un-stubbed {@code default} interface methods with the type
 * default, so marker interfaces such as {@code InterruptibleRelay} silently report
 * {@code false} and decorators get skipped.</p>
 */
public final class FakeRememberPathway implements Pathway<RememberSignal, RememberResult> {

    private final List<RememberSignal> received = new ArrayList<>();
    private final short soulVersion;
    private RuntimeException failWith;

    public FakeRememberPathway() {
        this((short) 0);
    }

    public FakeRememberPathway(final short soulVersion) {
        this.soulVersion = soulVersion;
    }

    /**
     * Makes every subsequent conduction throw, for degradation tests.
     *
     * @param failure exception to throw
     * @return this fake, for chaining
     */
    public FakeRememberPathway failing(final RuntimeException failure) {
        this.failWith = failure;
        return this;
    }

    /** @return every signal this fake was asked to conduct, in order */
    public List<RememberSignal> received() {
        return List.copyOf(received);
    }

    /** @return how many times this fake was invoked */
    public int invocationCount() {
        return received.size();
    }

    @Override
    public String name() {
        return "remember";
    }

    @Override
    public Class<RememberSignal> inputType() {
        return RememberSignal.class;
    }

    @Override
    public Class<RememberResult> outputType() {
        return RememberResult.class;
    }

    @Override
    public RememberResult conduct(final RememberSignal input) {
        received.add(input);
        if (failWith != null) {
            throw failWith;
        }
        return new RememberResult(input.id(), received.size() - 1, false,
                input.type(), input.source());
    }

    // ── Convenience wiring for tests ────────────────────────────────────────

    /**
     * Builds a catalog with this fake registered under {@code RememberPathway.class}.
     *
     * @return a catalog ready to hand to a {@link PathwayContext}
     */
    public DefaultPathwayCatalog inCatalog() {
        final DefaultPathwayCatalog catalog = new DefaultPathwayCatalog();
        catalog.register(RememberPathway.class, this);
        return catalog;
    }

    /**
     * Builds a context whose catalog resolves {@code RememberPathway} to this fake, with
     * this fake also bound as the {@link SoulVersionSource}.
     *
     * @param namespaceId namespace for the context
     * @return a bound pathway context
     */
    public PathwayContext inContext(final String namespaceId) {
        return DefaultPathwayContext.builder()
                .namespaceId(namespaceId)
                .catalog(inCatalog())
                .bind(SoulVersionSource.class, () -> soulVersion)
                .build();
    }
}
