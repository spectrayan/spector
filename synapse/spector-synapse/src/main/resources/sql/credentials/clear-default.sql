UPDATE credentials
SET is_default = FALSE
WHERE tenant_id = :tenantId AND provider = :provider
