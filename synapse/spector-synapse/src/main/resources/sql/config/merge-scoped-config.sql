MERGE INTO scoped_config (
    scope, category, config_json, updated_at, updated_by
) KEY (scope, category) VALUES (
    :scope, :category, :json, :updatedAt, :updatedBy
)
