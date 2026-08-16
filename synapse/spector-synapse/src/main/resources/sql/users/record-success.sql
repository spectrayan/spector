UPDATE users
SET failed_login_count = 0,
    locked_until = NULL,
    last_login_at = :now,
    updated_at = :now
WHERE user_id = :userId
