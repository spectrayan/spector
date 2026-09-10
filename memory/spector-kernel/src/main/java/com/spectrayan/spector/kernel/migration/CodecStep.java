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
