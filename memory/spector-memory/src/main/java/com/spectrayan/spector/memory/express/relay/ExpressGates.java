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

import java.util.function.Predicate;

public final class ExpressGates {
    public static final Predicate<ExpressSignal> HAS_PERSONA = signal -> signal != null && signal.personaContext() != null;
    public static final Predicate<ExpressSignal> IDIOLECT_ENABLED = signal -> HAS_PERSONA.test(signal) && signal.personaContext().idiolect() != null;
    public static final Predicate<ExpressSignal> PROSODY_ENABLED = signal -> signal != null && signal.interoceptiveState() != null;
    public static final Predicate<ExpressSignal> KINESICS_ENABLED = signal -> HAS_PERSONA.test(signal) && signal.personaContext().embodiedKinesics() != null;
    public static final Predicate<ExpressSignal> PHENOMENOLOGICAL_ENABLED = signal -> true; // Or however you want to gate it, user just said add them
    
    private ExpressGates() {}
}
