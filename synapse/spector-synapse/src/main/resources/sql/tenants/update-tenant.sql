UPDATE tenants
SET name = :name,
    description = :description,
    status = :status,
    plan = :plan,
    legal_hold = :legalHold,
    byok_enabled = :byokEnabled,
    break_glass_approver_required = :breakGlassApproverRequired,
    break_glass_max_duration_minutes = :breakGlassMaxDurationMinutes,
    max_accounts = :maxAccounts,
    max_namespaces = :maxNamespaces,
    max_storage_bytes = :maxStorageBytes,
    updated_at = :updatedAt
WHERE tenant_id = :tenantId
