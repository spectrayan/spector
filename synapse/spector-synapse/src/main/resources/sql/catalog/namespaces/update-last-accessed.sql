-- Update last accessed timestamp for a namespace
UPDATE namespaces
SET last_accessed_at = CURRENT_TIMESTAMP
WHERE namespace_id = :namespaceId
