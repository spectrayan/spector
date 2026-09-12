-- List all account catalog metadata with a non-null, non-blank tenant_id
SELECT
    user_id,
    display_name,
    kind,
    profile,
    flags,
    default_namespace_id,
    max_namespaces,
    max_hot_namespaces,
    membership_version,
    tenant_id,
    legal_hold,
    created_at
FROM users
WHERE tenant_id IS NOT NULL AND tenant_id != ''
