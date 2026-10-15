UPDATE namespaces
SET owner_account_id = :newOwnerAccountId
WHERE namespace_id = :namespaceId
