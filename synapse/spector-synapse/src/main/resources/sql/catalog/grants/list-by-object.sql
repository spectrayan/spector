-- List active grants for an object (namespace or bundle)
SELECT
    grant_id,
    object_type,
    object_id,
    principal_id,
    principal_type,
    role,
    actions,
    granted_by,
    granted_at,
    expires_at,
    revoked_at,
    constraints_json
FROM grants
WHERE object_type = :objectType
  AND object_id = :objectId
  AND revoked_at IS NULL
  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
ORDER BY granted_at DESC
