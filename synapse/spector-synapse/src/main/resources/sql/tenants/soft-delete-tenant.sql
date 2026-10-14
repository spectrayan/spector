UPDATE tenants
SET status = 'DELETED',
    updated_at = :now,
    deleted_at = :now
WHERE tenant_id = :tenantId
