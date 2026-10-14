SELECT
    audit_id,
    timestamp,
    actor_id,
    actor_tenant_id,
    action,
    target_type,
    target_id,
    details_json
FROM admin_audit_log
WHERE target_type = :targetType
  AND target_id = :targetId
ORDER BY timestamp DESC, audit_id DESC
