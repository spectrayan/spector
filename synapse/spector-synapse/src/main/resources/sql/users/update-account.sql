UPDATE users
SET active = :active,
    roles = :roles,
    scopes = :scopes,
    display_name = :displayName,
    updated_at = :now
WHERE user_id = :userId
