INSERT INTO memory_snapshots (
    snapshot_time, total_count, episodic_count, semantic_count, procedural_count, active_count
) VALUES (
    :snapshotTime, :totalCount, :episodicCount, :semanticCount, :proceduralCount, :activeCount
)
