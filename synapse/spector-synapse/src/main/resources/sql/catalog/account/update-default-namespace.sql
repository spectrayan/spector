-- Update account default namespace ID
UPDATE users
SET default_namespace_id = :defaultNamespaceId,
    updated_at = CURRENT_TIMESTAMP
WHERE user_id = :userId
