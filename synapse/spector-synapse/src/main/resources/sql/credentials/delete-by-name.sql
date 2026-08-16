DELETE FROM credentials
WHERE tenant_id = :tenantId AND name = :name
