UPDATE users
SET failed_login_count = :count,
    updated_at = :now
WHERE user_id = :userId
