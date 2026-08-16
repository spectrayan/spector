SELECT COUNT(*)
FROM users
WHERE LOWER(username) = LOWER(:username)
