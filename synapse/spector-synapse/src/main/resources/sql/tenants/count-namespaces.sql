SELECT COUNT(*)
FROM namespaces n
JOIN users u ON n.owner_account_id = u.user_id
WHERE COALESCE(NULLIF(u.tenant_id, ''), 'default') = :tenantId
  AND n.status != 'TOMBSTONED'
