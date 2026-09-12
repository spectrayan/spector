-- List every namespace owned by an account, including tombstoned ones.
-- Deliberately unfiltered on status: a tombstoned namespace still has bundle files on disk, so
-- migration and tenant-prefix wipe must be able to see it (Req R9.1). Callers that only care about
-- openable namespaces filter on status themselves.
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
WHERE owner_account_id = :ownerAccountId
