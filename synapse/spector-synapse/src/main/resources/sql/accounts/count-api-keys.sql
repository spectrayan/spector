SELECT COUNT(*)
FROM credentials
WHERE user_id = :accountId
  AND category = 'AUTH'
