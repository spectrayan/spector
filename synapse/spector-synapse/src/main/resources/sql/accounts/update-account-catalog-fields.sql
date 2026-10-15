UPDATE users
SET kind = :kind,
    profile = :profile,
    default_namespace_id = :defaultNamespaceId,
    max_namespaces = :maxNamespaces,
    max_hot_namespaces = :maxHotNamespaces,
    legal_hold = :legalHold,
    owner_account_id = :ownerAccountId,
    tenant_id = :tenantId,
    updated_at = :now
WHERE user_id = :accountId
