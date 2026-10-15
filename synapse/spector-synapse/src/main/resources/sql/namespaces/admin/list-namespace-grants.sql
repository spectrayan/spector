SELECT
    g.grant_id,
    g.object_id AS namespace_id,
    g.principal_id,
    COALESCE(u.username, g.principal_id) AS principal_username,
    g.principal_type,
    g.role,
    g.actions,
    g.granted_by,
    g.granted_at,
    g.expires_at
FROM grants g
LEFT JOIN users u ON g.principal_id = u.user_id
WHERE g.object_type = 'NAMESPACE'
  AND g.object_id = :namespaceId
  AND g.revoked_at IS NULL
  AND (g.expires_at IS NULL OR g.expires_at > CURRENT_TIMESTAMP)
ORDER BY g.granted_at ASC
