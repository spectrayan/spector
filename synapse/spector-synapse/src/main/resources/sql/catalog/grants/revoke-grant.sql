-- Revoke a grant by setting revoked_at timestamp
UPDATE grants
SET revoked_at = CURRENT_TIMESTAMP
WHERE grant_id = :grantId
  AND revoked_at IS NULL
