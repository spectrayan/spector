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

import java.util.List;
import java.util.Map;

/**
 * Response for the memory table view endpoint.
 *
 * @param rows             paginated list of memory rows
 * @param totalCount       total number of records matching the filter (before pagination)
 * @param page             current page number (0-based)
 * @param pageSize         number of rows per page
 * @param tierCounts       count of records per tier
 * @param tombstoneRatios  tombstone ratio per tier (0.0 to 1.0)
 */
public record MemoryTableDto(
    List<MemoryRowDto> rows,
    int totalCount,
    int page,
    int pageSize,
    Map<String, Integer> tierCounts,
    Map<String, Float> tombstoneRatios
) {}
