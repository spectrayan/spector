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
package com.spectrayan.spector.synapse.memory;

import java.util.List;

public class FactDto {
    public record FactAssertRequest(
            String subject,
            String predicate,
            String object,
            Long validFrom,
            Long validTo,
            Float confidence,
            Boolean allowCoexisting
    ) {}

    public record FactAssertResponse(
            int factId,
            String status
    ) {}

    public record FactRetractResponse(
            int retractionId,
            String status
    ) {}
}
