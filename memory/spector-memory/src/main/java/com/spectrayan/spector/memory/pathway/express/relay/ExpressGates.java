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
package com.spectrayan.spector.memory.pathway.express.relay;

import java.util.function.Predicate;

public final class ExpressGates {
    public static final Predicate<ExpressSignal> HAS_PERSONA = signal -> signal != null && signal.personaContext() != null;
    public static final Predicate<ExpressSignal> IDIOLECT_ENABLED = signal -> HAS_PERSONA.test(signal) && signal.personaContext().idiolect() != null;
    public static final Predicate<ExpressSignal> PROSODY_ENABLED = signal -> signal != null && signal.interoceptiveState() != null;
    public static final Predicate<ExpressSignal> KINESICS_ENABLED = signal -> HAS_PERSONA.test(signal) && signal.personaContext().embodiedKinesics() != null;
    public static final Predicate<ExpressSignal> PHENOMENOLOGICAL_ENABLED = signal -> true; // Or however you want to gate it, user just said add them
    
    private ExpressGates() {}
}
