INSERT INTO refresh_tokens (token_id, user_id, token_hash, expires_at, revoked)
VALUES (:tokenId, :userId, :tokenHash, :expiresAt, FALSE)
