UPDATE users
SET tenant_id = :tenantId,
    updated_at = :now
WHERE user_id = :accountId
