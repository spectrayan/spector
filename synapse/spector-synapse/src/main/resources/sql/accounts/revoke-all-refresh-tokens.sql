UPDATE refresh_tokens
SET revoked = TRUE
WHERE user_id = :accountId
  AND revoked = FALSE
