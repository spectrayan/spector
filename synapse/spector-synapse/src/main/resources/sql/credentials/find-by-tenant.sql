SELECT credential_id, tenant_id, user_id, name, category, provider, credential_type,
       ciphertext, iv, auth_tag, masked_preview, properties_json, is_default,
       description, version, created_at, updated_at, expires_at, last_used_at
FROM credentials
WHERE tenant_id = :tenantId
ORDER BY provider, name
