UPDATE grants
SET revoked_at = CURRENT_TIMESTAMP
WHERE object_type = 'NAMESPACE'
  AND object_id = :namespaceId
  AND role = 'OWNER'
  AND revoked_at IS NULL
