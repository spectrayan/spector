MERGE INTO jti_blocklist (jti, expires_at) KEY (jti) VALUES (:jti, :expiresAt)
