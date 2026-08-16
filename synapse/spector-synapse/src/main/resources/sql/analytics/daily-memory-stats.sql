SELECT CAST(snapshot_time AS DATE) as s_date, MAX(total_count) as max_count
FROM memory_snapshots
WHERE snapshot_time >= :since
GROUP BY CAST(snapshot_time AS DATE)
ORDER BY s_date ASC
