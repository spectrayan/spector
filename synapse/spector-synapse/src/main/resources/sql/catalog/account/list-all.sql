-- List all account catalog metadata
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
