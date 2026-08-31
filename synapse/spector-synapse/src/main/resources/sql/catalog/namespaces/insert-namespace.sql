-- Insert new namespace record
INSERT INTO namespaces (
    namespace_id,
    owner_account_id,
    slug,
    type,
    status,
    display_name,
    description,
    bias_json,
    created_at,
    last_accessed_at
) VALUES (
    :namespaceId,
    :ownerAccountId,
    :slug,
    :type,
    :status,
    :displayName,
    :description,
    :biasJson,
    :createdAt,
    :lastAccessedAt
)
