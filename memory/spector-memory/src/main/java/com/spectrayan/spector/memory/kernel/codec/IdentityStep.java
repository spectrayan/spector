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
package com.spectrayan.spector.memory.kernel.codec;

import java.io.IOException;

/**
 * No-op identity step for current format files.
 */
public final class IdentityStep implements CodecStep {

    private final FormatId format;

    public IdentityStep(FormatId format) {
        this.format = format;
    }

    @Override
    public FormatId from() {
        return format;
    }

    @Override
    public FormatId to() {
        return format;
    }

    @Override
    public Kind kind() {
        return Kind.IDENTITY;
    }

    @Override
    public void apply(MigrationContext ctx) throws IOException {
        // No-op identity step
    }
}
