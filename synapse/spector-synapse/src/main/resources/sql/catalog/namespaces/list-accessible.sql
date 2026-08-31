-- List all accessible namespaces for an account (owned + granted)
SELECT DISTINCT
    n.namespace_id,
    n.owner_account_id,
    n.slug,
    n.type,
    n.status,
    n.display_name,
    n.description,
    n.bias_json,
    n.created_at,
    n.last_accessed_at
FROM namespaces n
LEFT JOIN grants g
    ON g.object_type = 'NAMESPACE'
   AND g.object_id = n.namespace_id
   AND g.principal_id = :accountId
   AND g.revoked_at IS NULL
   AND (g.expires_at IS NULL OR g.expires_at > CURRENT_TIMESTAMP)
WHERE (n.owner_account_id = :accountId OR g.grant_id IS NOT NULL)
  AND n.status != 'TOMBSTONED'
ORDER BY n.created_at ASC
