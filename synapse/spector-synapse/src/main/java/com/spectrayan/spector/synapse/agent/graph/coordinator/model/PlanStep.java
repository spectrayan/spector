/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.graph.coordinator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an individual step within a Plan-and-Execute workflow.
 *
 * @param step           1-based sequence index
 * @param description    actionable goal/description of this step
 * @param tool           optional tool required for this step
 * @param assignedAgent  optional specialist agent assigned to execute this step
 * @param status         lifecycle status (PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED)
 * @param result         execution result/output produced by this step
 * @param flowSpecJson   optional dynamic subgraph specification for this step
 */
public record PlanStep(
        int step,
        String description,
        String tool,
        String assignedAgent,
        String status,
        String result,
        String flowSpecJson
) {

    public static final String STATUS_PENDING     = "PENDING";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED   = "COMPLETED";
    public static final String STATUS_FAILED      = "FAILED";
    public static final String STATUS_SKIPPED     = "SKIPPED";

    public PlanStep {
        status = (status != null && !status.isBlank()) ? status : STATUS_PENDING;
        description = (description != null) ? description : "";
        result = (result != null) ? result : "";
    }

    public static PlanStep of(int step, String description) {
        return new PlanStep(step, description, null, null, STATUS_PENDING, "", null);
    }

    public static PlanStep of(int step, String description, String tool, String assignedAgent) {
        return new PlanStep(step, description, tool, assignedAgent, STATUS_PENDING, "", null);
    }

    public PlanStep withStatus(String newStatus, String newResult) {
        return new PlanStep(step, description, tool, assignedAgent, newStatus, newResult, flowSpecJson);
    }

    public PlanStep withFlowSpec(String newFlowSpecJson) {
        return new PlanStep(step, description, tool, assignedAgent, status, result, newFlowSpecJson);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return STATUS_PENDING.equalsIgnoreCase(status);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("step", step);
        map.put("description", description);
        if (tool != null && !tool.isBlank()) map.put("tool", tool);
        if (assignedAgent != null && !assignedAgent.isBlank()) map.put("assigned_agent", assignedAgent);
        map.put("status", status);
        if (result != null && !result.isBlank()) map.put("result", result);
        if (flowSpecJson != null && !flowSpecJson.isBlank()) map.put("flow_spec_json", flowSpecJson);
        return map;
    }

    public static PlanStep fromMap(Map<String, Object> map) {
        if (map == null) return null;
        int step = 1;
        Object stepObj = map.get("step");
        if (stepObj instanceof Number n) {
            step = n.intValue();
        } else if (stepObj != null) {
            try { step = Integer.parseInt(stepObj.toString()); } catch (NumberFormatException ignored) {}
        }

        String desc = map.getOrDefault("description", "").toString();
        String tool = map.containsKey("tool") && map.get("tool") != null ? map.get("tool").toString() : null;
        String agent = map.containsKey("assigned_agent") && map.get("assigned_agent") != null
                ? map.get("assigned_agent").toString()
                : (map.containsKey("agent") && map.get("agent") != null ? map.get("agent").toString() : null);
        String status = map.getOrDefault("status", STATUS_PENDING).toString();
        String result = map.getOrDefault("result", "").toString();
        String flowSpecJson = map.containsKey("flow_spec_json") && map.get("flow_spec_json") != null
                ? map.get("flow_spec_json").toString()
                : null;

        return new PlanStep(step, desc, tool, agent, status, result, flowSpecJson);
    }
}
