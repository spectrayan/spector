SELECT config_json, updated_at, updated_by
FROM scoped_config
WHERE scope = :scope AND category = :category
