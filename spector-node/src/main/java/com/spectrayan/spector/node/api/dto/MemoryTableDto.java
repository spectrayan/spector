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
