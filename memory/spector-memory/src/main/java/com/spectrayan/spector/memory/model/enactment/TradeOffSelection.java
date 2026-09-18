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
package com.spectrayan.spector.memory.model.enactment;

/**
 * Explicit trade-off evaluation made by the persona during deliberation (ADR-0032).
 */
public record TradeOffSelection(
        String prioritizedValue,
        String sacrificedValue,
        String rationale
) {
    public TradeOffSelection {
        prioritizedValue = (prioritizedValue != null) ? prioritizedValue : "DEFAULT";
        sacrificedValue = (sacrificedValue != null) ? sacrificedValue : "NONE";
        rationale = (rationale != null) ? rationale : "";
    }

    public static TradeOffSelection balanced(String rationale) {
        return new TradeOffSelection("BALANCE", "EXTREMES", rationale);
    }
}
