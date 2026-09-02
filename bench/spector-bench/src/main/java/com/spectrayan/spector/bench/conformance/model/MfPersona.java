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
 * Persona metadata from MF-001 persona.json.
 */
public record MfPersona(
        String rememberer,
        String name,
        Integer age,
        String occupation,
        String lifeContext,
        List<String> interests,
        List<String> likes,
        List<String> dislikes,
        List<String> personalityTraits,
        Map<String, Double> bigFive,
        String soulRule
) {
    public MfPersona {
        interests = interests != null ? List.copyOf(interests) : List.of();
        likes = likes != null ? List.copyOf(likes) : List.of();
        dislikes = dislikes != null ? List.copyOf(dislikes) : List.of();
        personalityTraits = personalityTraits != null ? List.copyOf(personalityTraits) : List.of();
        bigFive = bigFive != null ? Map.copyOf(bigFive) : Map.of();
    }
}
