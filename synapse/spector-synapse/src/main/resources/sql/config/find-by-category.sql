SELECT scope, config_json, updated_at, updated_by
FROM scoped_config
WHERE category = :category
ORDER BY scope
