UPDATE namespaces
SET max_memories = :maxMemories,
    max_storage_bytes = :maxStorageBytes,
    max_ops_per_minute = :maxOpsPerMinute
WHERE namespace_id = :namespaceId
