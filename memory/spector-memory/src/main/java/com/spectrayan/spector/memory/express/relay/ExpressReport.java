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
package com.spectrayan.spector.memory.express.relay;

import com.spectrayan.spector.memory.model.ProsodyParameterVector;
import com.spectrayan.spector.memory.model.IdiolectProfile;
import java.time.Duration;

public record ExpressReport(
    ProsodyParameterVector prosodyVector,
    IdiolectProfile idiolectProfile,
    String promptDirectives,
    String ssmlTags,
    Duration elapsed,
    int relaysExecuted
) {
}
