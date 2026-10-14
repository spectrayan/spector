SELECT
    g.grant_id,
    g.object_id AS namespace_id,
    n.slug AS namespace_slug,
    n.display_name AS namespace_display_name,
    n.owner_account_id AS namespace_owner_id,
    g.role,
    g.actions,
    g.granted_by,
    g.granted_at,
    g.expires_at
FROM grants g
LEFT JOIN namespaces n ON g.object_id = n.namespace_id
WHERE g.principal_id = :accountId
  AND g.object_type = 'NAMESPACE'
  AND g.revoked_at IS NULL
  AND (g.expires_at IS NULL OR g.expires_at > CURRENT_TIMESTAMP)
ORDER BY g.granted_at ASC
