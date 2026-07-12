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
package com.spectrayan.spector.node.api.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.spectrayan.spector.query.SearchResponse;

/**
 * Response DTO for the search endpoint ({@code POST /api/v1/search}).
 *
 * @param results     scored search results
 * @param totalHits   total number of matches
 * @param queryTimeMs query execution time in milliseconds
 * @param mode        search mode used (KEYWORD, VECTOR, HYBRID)
 */
public record SearchResponseDto(
        List<Map<String, Object>> results,
        int totalHits,
        long queryTimeMs,
        String mode
) {

    /**
     * Creates a DTO from the engine's search response.
     */
    public static SearchResponseDto from(SearchResponse response) {
        var resultList = Arrays.stream(response.results())
                .map(r -> Map.<String, Object>of(
                        "id", r.id(),
                        "score", r.score()
                ))
                .toList();

        return new SearchResponseDto(
                resultList,
                response.totalHits(),
                response.queryTimeMs(),
                response.mode().name()
        );
    }
}
