SELECT user_id, username, password_hash, email, display_name, roles, scopes,
       must_change_password, active, failed_login_count, locked_until,
       last_login_at, created_at, updated_at
FROM users
WHERE user_id = :userId
