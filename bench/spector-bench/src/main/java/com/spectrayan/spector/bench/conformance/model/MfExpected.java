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
package com.spectrayan.spector.bench.conformance.model;

import java.util.List;
import java.util.Map;

/**
 * Expected test contract for an MF-001 fixture.
 */
public record MfExpected(
        String testId,
        long evalAsOfMs,
        String rememberer,
        String notes,
        Map<String, String> load,
        List<MfAssertion> assertions,
        Map<String, Object> negativeControls,
        List<String> illegalSetups
) {
    public MfExpected {
        load = load != null ? Map.copyOf(load) : Map.of();
        assertions = assertions != null ? List.copyOf(assertions) : List.of();
        negativeControls = negativeControls != null ? Map.copyOf(negativeControls) : Map.of();
        illegalSetups = illegalSetups != null ? List.copyOf(illegalSetups) : List.of();
    }
}
