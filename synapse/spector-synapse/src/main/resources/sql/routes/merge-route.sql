MERGE INTO connector_routes (
    route_id, tenant_id, name, template_id, connector_type,
    source, schedule, enabled, parameters_json, created_at, updated_at
) KEY (route_id) VALUES (
    :routeId, :tenantId, :name, :templateId, :connectorType,
    :source, :schedule, :enabled, :json, :createdAt, :updatedAt
)
