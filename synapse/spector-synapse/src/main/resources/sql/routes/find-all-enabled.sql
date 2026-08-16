SELECT route_id, tenant_id, name, template_id, connector_type,
       source, schedule, enabled, parameters_json, created_at, updated_at, last_executed_at
FROM connector_routes
WHERE enabled = TRUE
ORDER BY route_id
