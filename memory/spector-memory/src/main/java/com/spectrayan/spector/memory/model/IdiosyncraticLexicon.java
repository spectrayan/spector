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
package com.spectrayan.spector.memory.model;

import java.util.List;
import java.util.Map;

public record IdiosyncraticLexicon(
        List<String> catchphrases,
        List<String> greetings,
        List<String> favoriteMetaphors,
        List<String> colloquialisms,
        List<String> domainJargon,
        List<String> tabooWords,
        Map<String, String> wordReplacements
) {
    public static final IdiosyncraticLexicon EMPTY = new IdiosyncraticLexicon(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()
    );

    public IdiosyncraticLexicon {
        catchphrases = catchphrases != null ? List.copyOf(catchphrases) : List.of();
        greetings = greetings != null ? List.copyOf(greetings) : List.of();
        favoriteMetaphors = favoriteMetaphors != null ? List.copyOf(favoriteMetaphors) : List.of();
        colloquialisms = colloquialisms != null ? List.copyOf(colloquialisms) : List.of();
        domainJargon = domainJargon != null ? List.copyOf(domainJargon) : List.of();
        tabooWords = tabooWords != null ? List.copyOf(tabooWords) : List.of();
        wordReplacements = wordReplacements != null ? Map.copyOf(wordReplacements) : Map.of();
    }
    
    public boolean hasCatchphrases() { return !catchphrases.isEmpty(); }
    public boolean hasGreetings() { return !greetings.isEmpty(); }
    public boolean hasFavoriteMetaphors() { return !favoriteMetaphors.isEmpty(); }
    public boolean hasColloquialisms() { return !colloquialisms.isEmpty(); }
    public boolean hasDomainJargon() { return !domainJargon.isEmpty(); }
    public boolean hasTabooWords() { return !tabooWords.isEmpty(); }
    public boolean hasWordReplacements() { return !wordReplacements.isEmpty(); }
}
