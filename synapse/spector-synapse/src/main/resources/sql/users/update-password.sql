UPDATE users
SET password_hash = :hash,
    must_change_password = FALSE,
    updated_at = :now
WHERE user_id = :userId
