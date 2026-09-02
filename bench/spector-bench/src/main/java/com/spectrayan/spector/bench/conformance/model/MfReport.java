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
 * Conformance report output matching MF-001 specification shape.
 */
public record MfReport(
        String testId,
        String engine,
        String condition,
        List<String> passed,
        List<FailedAssertion> failed
) {
    public record FailedAssertion(
            String id,
            Map<String, Object> got,
            String reason
    ) {}

    public boolean isAllPassed() {
        return failed == null || failed.isEmpty();
    }
}
