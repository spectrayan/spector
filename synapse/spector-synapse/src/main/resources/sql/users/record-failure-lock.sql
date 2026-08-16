UPDATE users
SET failed_login_count = :count,
    locked_until = :lockUntil,
    updated_at = :now
WHERE user_id = :userId
