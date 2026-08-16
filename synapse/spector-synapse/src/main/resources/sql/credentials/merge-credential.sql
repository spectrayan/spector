MERGE INTO credentials (
    credential_id, tenant_id, user_id, name, category, provider, credential_type,
    ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
    description, version, created_at, updated_at, expires_at
) KEY (tenant_id, name) VALUES (
    :credentialId, :tenantId, :userId, :name, :category, :provider, :credentialType,
    :ciphertext, :iv, :authTag, :maskedPreview, :propertiesJson, :isDefault,
    :description, :version, :createdAt, :updatedAt, :expiresAt
)
