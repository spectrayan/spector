SELECT namespace_id
FROM namespaces
WHERE owner_account_id = :accountId
  AND status != 'TOMBSTONED'
