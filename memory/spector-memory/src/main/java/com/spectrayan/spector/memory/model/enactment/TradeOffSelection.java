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
