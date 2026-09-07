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

import java.util.List;

/**
 * Structured output of System 2 Bounded Persona Deliberation (ADR-0032).
 */
public record PersonaDeliberation(
        String internalMonologue,
        String activeDogma,
        TradeOffSelection tradeOffs,
        List<String> blindSpots,
        String tacticalFirstMove
) {
    public PersonaDeliberation {
        internalMonologue = (internalMonologue != null) ? internalMonologue : "";
        activeDogma = (activeDogma != null) ? activeDogma : "";
        tradeOffs = (tradeOffs != null) ? tradeOffs : TradeOffSelection.balanced("");
        blindSpots = (blindSpots != null) ? List.copyOf(blindSpots) : List.of();
        tacticalFirstMove = (tacticalFirstMove != null) ? tacticalFirstMove : "";
    }

    public static PersonaDeliberation empty() {
        return new PersonaDeliberation("", "", TradeOffSelection.balanced(""), List.of(), "");
    }
}
