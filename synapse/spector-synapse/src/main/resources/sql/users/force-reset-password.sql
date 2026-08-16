UPDATE users
SET password_hash = :hash,
    must_change_password = FALSE,
    failed_login_count = 0,
    locked_until = NULL,
    updated_at = :now
WHERE user_id = :userId
