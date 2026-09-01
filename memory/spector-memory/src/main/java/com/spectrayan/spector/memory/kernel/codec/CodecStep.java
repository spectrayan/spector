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
 * A single migration hop from one concrete format to another.
 */
public sealed interface CodecStep
        permits InPlaceHeaderStep, RewriteFileStep, IdentityStep {

    FormatId from();
    FormatId to();
    Kind kind();

    void apply(MigrationContext ctx) throws IOException;

    enum Kind {
        IN_PLACE_HEADER,
        REWRITE,
        IDENTITY
    }
}
