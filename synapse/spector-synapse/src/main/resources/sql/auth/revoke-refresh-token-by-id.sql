UPDATE refresh_tokens
SET revoked = TRUE
WHERE token_id = :tokenId AND revoked = FALSE
