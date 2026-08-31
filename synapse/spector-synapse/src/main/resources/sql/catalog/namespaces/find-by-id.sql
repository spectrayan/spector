-- Find namespace by immutable TSID
SELECT
    namespace_id,
    owner_account_id,
    slug,
    type,
    status,
    display_name,
    description,
    bias_json,
    created_at,
    last_accessed_at,
    legal_hold
FROM namespaces
WHERE namespace_id = :namespaceId
