SELECT
    n.namespace_id,
    n.slug,
    n.display_name,
    n.description,
    n.type,
    n.status,
    n.legal_hold,
    n.owner_account_id,
    COALESCE(u.username, n.owner_account_id) AS owner_username,
    COALESCE(u.display_name, n.owner_account_id) AS owner_display_name,
    COALESCE(NULLIF(u.tenant_id, ''), 'default') AS tenant_id,
    n.max_memories,
    n.max_storage_bytes,
    n.max_ops_per_minute,
    n.created_at,
    n.last_write_at,
    n.last_accessed_at,
    (SELECT COUNT(*) FROM grants g
     WHERE g.object_type = 'NAMESPACE'
       AND g.object_id = n.namespace_id
       AND g.revoked_at IS NULL
       AND (g.expires_at IS NULL OR g.expires_at > CURRENT_TIMESTAMP)) AS grants_count
FROM namespaces n
LEFT JOIN users u ON n.owner_account_id = u.user_id
WHERE n.namespace_id = :namespaceId
