INSERT INTO users (
    user_id, username, password_hash, email, display_name,
    roles, scopes, must_change_password, active,
    failed_login_count, created_at, updated_at
) VALUES (
    :userId, :username, :passwordHash, :email, :displayName,
    :roles, :scopes, :mustChange, TRUE, 0, :now, :now
)
