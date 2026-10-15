UPDATE users
SET display_name = :displayName,
    email = :email,
    active = :active,
    roles = :roles,
    scopes = :scopes,
    profile = :profile,
    legal_hold = :legalHold,
    owner_account_id = :ownerAccountId,
    max_namespaces = :maxNamespaces,
    max_hot_namespaces = :maxHotNamespaces,
    updated_at = :now
WHERE user_id = :accountId
