SELECT
    tenant_id,
    name,
    description,
    status,
    plan,
    legal_hold,
    byok_enabled,
    break_glass_approver_required,
    break_glass_max_duration_minutes,
    max_accounts,
    max_namespaces,
    max_storage_bytes,
    created_at,
    updated_at,
    deleted_at
FROM tenants
WHERE tenant_id = :tenantId
