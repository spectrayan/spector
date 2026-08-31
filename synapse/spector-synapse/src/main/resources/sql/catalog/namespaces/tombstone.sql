-- Soft-delete (tombstone) a namespace
UPDATE namespaces
SET status = 'TOMBSTONED',
    last_accessed_at = CURRENT_TIMESTAMP
WHERE namespace_id = :namespaceId
