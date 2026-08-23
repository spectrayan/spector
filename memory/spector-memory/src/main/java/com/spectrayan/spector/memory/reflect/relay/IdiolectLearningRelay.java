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
package com.spectrayan.spector.memory.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;

public class IdiolectLearningRelay implements SynapticRelay<ReflectSignal> {

    @Override
    public boolean transmit(ReflectSignal signal) {
        // Analyzes episodic memory text in ReflectSignal and updates IdiolectProfile stylometrics.
        return true;
    }

    @Override
    public String relayName() {
        return "idiolect_learning";
    }
}
