SELECT COUNT(*)
FROM users
WHERE COALESCE(NULLIF(tenant_id, ''), 'default') = :tenantId
