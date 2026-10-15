SELECT
    (SELECT MAX(last_accessed_at) FROM namespaces WHERE owner_account_id = :accountId) AS max_ns_access,
    (SELECT MAX(last_used_at) FROM credentials WHERE user_id = :accountId) AS max_cred_use
