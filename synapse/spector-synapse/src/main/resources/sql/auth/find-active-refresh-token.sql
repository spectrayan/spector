SELECT token_id, user_id, expires_at
FROM refresh_tokens
WHERE token_hash = :tokenHash
  AND revoked = FALSE
  AND expires_at > :now
