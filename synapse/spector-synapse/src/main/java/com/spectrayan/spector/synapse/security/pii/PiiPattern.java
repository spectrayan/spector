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
package com.spectrayan.spector.synapse.security.pii;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A compiled regex pattern for one PII category, gated by {@link PiiLevel}.
 *
 * @param id       stable pattern identifier (from YAML)
 * @param type     PII category
 * @param pattern  compiled regex
 * @param minLevel minimum sensitivity level that activates this pattern
 */
public record PiiPattern(String id, PiiType type, Pattern pattern, PiiLevel minLevel) {

    public PiiPattern {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(minLevel, "minLevel");
    }

    public boolean appliesAt(PiiLevel level) {
        return level != null && level.includes(minLevel);
    }
}
