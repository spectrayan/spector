UPDATE credentials
SET last_used_at = :now
WHERE credential_id = :id
