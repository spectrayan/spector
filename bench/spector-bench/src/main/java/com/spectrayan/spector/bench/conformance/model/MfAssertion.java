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

/**
 * Closed-form assertion specification predicate from MF-001 §10 expected.json.
 */
public record MfAssertion(
        String id,
        String query,
        String rememberer,
        String require,
        List<String> ids,
        Integer atMostRank,
        String higher,
        String lower,
        Integer k,
        Boolean soft,
        String property,
        String because
) {
    public MfAssertion {
        ids = ids != null ? List.copyOf(ids) : List.of();
        soft = soft != null ? soft : false;
    }
}
