-- Insert grant record
INSERT INTO grants (
    grant_id,
    object_type,
    object_id,
    principal_id,
    principal_type,
    role,
    actions,
    granted_by,
    granted_at,
    expires_at,
    revoked_at,
    constraints_json
) VALUES (
    :grantId,
    :objectType,
    :objectId,
    :principalId,
    :principalType,
    :role,
    :actions,
    :grantedBy,
    :grantedAt,
    :expiresAt,
    :revokedAt,
    :constraintsJson
)
