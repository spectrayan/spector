SELECT
    u.user_id,
    u.username,
    u.display_name,
    COALESCE(NULLIF(u.kind, ''), 'AGENT') AS kind,
    COALESCE(NULLIF(u.profile, ''), 'AGENT') AS profile,
    u.active,
    u.created_at
FROM users u
WHERE u.owner_account_id = :ownerAccountId
ORDER BY u.created_at ASC, u.username ASC
