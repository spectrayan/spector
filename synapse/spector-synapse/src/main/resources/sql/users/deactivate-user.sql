UPDATE users
SET active = FALSE,
    updated_at = :now
WHERE user_id = :userId
