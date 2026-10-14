SELECT
    u.user_id,
    u.username,
    u.email,
    u.display_name,
    u.roles,
    u.scopes,
    u.must_change_password,
    u.active,
    u.failed_login_count,
    u.locked_until,
    u.last_login_at,
    u.created_at,
    u.updated_at,
    COALESCE(NULLIF(u.tenant_id, ''), 'default') AS tenant_id,
    COALESCE(NULLIF(u.kind, ''), 'HUMAN') AS kind,
    COALESCE(NULLIF(u.profile, ''), 'HUMAN_SOLO') AS profile,
    u.default_namespace_id,
    u.max_namespaces,
    u.max_hot_namespaces,
    u.legal_hold,
    u.owner_account_id,
    owner.username AS owner_username,
    owner.display_name AS owner_display_name
FROM users u
LEFT JOIN users owner ON u.owner_account_id = owner.user_id
WHERE u.user_id = :accountId
