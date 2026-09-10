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
package com.spectrayan.spector.kernel.migration;

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
