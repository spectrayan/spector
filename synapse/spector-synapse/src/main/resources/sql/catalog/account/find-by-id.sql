-- Find account catalog metadata by user ID
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
    created_at
FROM users
WHERE user_id = :userId
