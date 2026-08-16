UPDATE refresh_tokens
SET revoked = TRUE
WHERE token_hash = :tokenHash AND revoked = FALSE
