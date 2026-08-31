-- Update namespace mutable metadata
UPDATE namespaces
SET display_name = :displayName,
    description = :description,
    type = :type,
    bias_json = :biasJson,
    last_accessed_at = CURRENT_TIMESTAMP
WHERE namespace_id = :namespaceId
