DELETE FROM jti_blocklist
WHERE expires_at IS NOT NULL AND expires_at < :now
