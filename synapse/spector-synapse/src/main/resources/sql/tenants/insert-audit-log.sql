INSERT INTO admin_audit_log (
    audit_id,
    timestamp,
    actor_id,
    actor_tenant_id,
    action,
    target_type,
    target_id,
    details_json
) VALUES (
    :auditId,
    :timestamp,
    :actorId,
    :actorTenantId,
    :action,
    :targetType,
    :targetId,
    :detailsJson
)
