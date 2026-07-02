/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.graph;

/**
 * Collects health metrics during graph decay cycles for observability.
 *
 * <p>A single instance is created per reflection cycle and populated by
 * {@code HebbianGraph.decayEdges()} and {@code EntityGraph.decayEdges()}.
 * After the cycle completes, this snapshot is attached to
 * {@link com.spectrayan.spector.memory.model.ReflectReport} for
 * downstream consumption (enterprise dashboard, logging, telemetry).</p>
 *
 * <p><b>Thread safety:</b> Not thread-safe — intended for single-threaded
 * use within a reflection cycle (graph lock is already held during decay).</p>
 *
 * <h3>Metrics Collected</h3>
 * <ul>
 *   <li><b>Hebbian:</b> edges decayed, bridge-protected, arousal-modulated, total surviving</li>
 *   <li><b>Entity:</b> edges decayed, bridge-protected, total surviving</li>
 *   <li><b>Bridge distribution:</b> quartile counts (0-63, 64-127, 128-191, 192-255)</li>
 *   <li><b>Edge age:</b> sum of ages for average computation</li>
 * </ul>
 */
public final class GraphHealthMetrics {

    // ── Hebbian Graph Metrics ──

    /** Hebbian edges removed during decay (weight fell below threshold). */
    private int hebbianEdgesDecayed;

    /** Hebbian edges that survived despite low weight due to high bridge score. */
    private int hebbianBridgeProtected;

    /** Hebbian edges whose decay rate was modulated by arousal. */
    private int hebbianArousalModulated;

    /** Total surviving Hebbian edges after decay. */
    private int hebbianEdgesSurviving;

    // ── Entity Graph Metrics ──

    /** Entity edges removed during decay. */
    private int entityEdgesDecayed;

    /** Entity edges that survived due to high bridge score. */
    private int entityBridgeProtected;

    /** Total surviving entity edges after decay. */
    private int entityEdgesSurviving;

    // ── Bridge Score Distribution (both graphs combined) ──

    /** Edges with bridge score 0-63 (low structural importance). */
    private int bridgeQ1;

    /** Edges with bridge score 64-127 (moderate structural importance). */
    private int bridgeQ2;

    /** Edges with bridge score 128-191 (high structural importance). */
    private int bridgeQ3;

    /** Edges with bridge score 192-255 (critical bridge edge). */
    private int bridgeQ4;

    // ── Edge Age Metrics ──

    /** Sum of all edge ages (currentCycle - lastCycle) for average computation. */
    private long edgeAgeSum;

    /** Count of edges with valid age data (for computing average). */
    private int edgeAgeCount;

    /** Maximum edge age observed. */
    private int edgeAgeMax;

    // ── Importance Score Metrics ──

    /** Sum of all importance scores (for computing average). */
    private float importanceScoreSum;

    /** Count of importance scores recorded. */
    private int importanceScoreCount;

    // ── Fragmentation ──

    /** Number of connected components in the Hebbian graph. */
    private int hebbianComponents;

    /** Number of non-isolated nodes in the Hebbian graph. */
    private int hebbianActiveNodes;

    // ── Entity Hierarchy Depth ──

    /** Maximum BFS depth observed across all entity pairs. */
    private int entityMaxDepth;

    /** Sum of BFS depths for computing average. */
    private long entityDepthSum;

    /** Count of entity pairs with measured depth (for average). */
    private int entityDepthCount;

    /** Depth distribution: entities reachable within 1 hop. */
    private int depthBucket1;

    /** Depth distribution: entities reachable at 2 hops. */
    private int depthBucket2;

    /** Depth distribution: entities reachable at 3 hops. */
    private int depthBucket3;

    /** Depth distribution: entities reachable at 4+ hops. */
    private int depthBucket4Plus;

    // ── Cross-Capture (STC) Metrics ──

    /** Entity edges boosted by STC cross-capture from strong Hebbian edges. */
    private int crossCapturedEdges;

    // ═══════════════════════════════════════════════════════════
    // Increment Methods (called during decay)
    // ═══════════════════════════════════════════════════════════

    /** Records a Hebbian edge removal. */
    public void recordHebbianDecay() { hebbianEdgesDecayed++; }

    /** Records a Hebbian edge surviving due to bridge score. */
    public void recordHebbianBridgeProtection() { hebbianBridgeProtected++; }

    /** Records a Hebbian edge with arousal-modulated decay. */
    public void recordHebbianArousalModulation() { hebbianArousalModulated++; }

    /** Records a surviving Hebbian edge with its metadata. */
    public void recordHebbianSurvivor(int bridgeScore, int edgeAge) {
        hebbianEdgesSurviving++;
        recordBridgeQuartile(bridgeScore);
        recordEdgeAge(edgeAge);
    }

    /** Records an entity edge removal. */
    public void recordEntityDecay() { entityEdgesDecayed++; }

    /** Records an entity edge surviving due to bridge score. */
    public void recordEntityBridgeProtection() { entityBridgeProtected++; }

    /** Records a surviving entity edge with its metadata. */
    public void recordEntitySurvivor(int bridgeScore) {
        entityEdgesSurviving++;
        recordBridgeQuartile(bridgeScore);
    }

    /** Records an importance score observation. */
    public void recordImportanceScore(float score) {
        importanceScoreSum += score;
        importanceScoreCount++;
    }

    /** Sets the Hebbian graph fragmentation data. */
    public void setHebbianFragmentation(int components, int activeNodes) {
        this.hebbianComponents = components;
        this.hebbianActiveNodes = activeNodes;
    }

    /** Records an entity edge boosted by STC cross-capture. */
    public void recordCrossCapture() { crossCapturedEdges++; }

    // ═══════════════════════════════════════════════════════════
    // Query Methods (called after decay for reporting)
    // ═══════════════════════════════════════════════════════════

    public int hebbianEdgesDecayed()     { return hebbianEdgesDecayed; }
    public int hebbianBridgeProtected()  { return hebbianBridgeProtected; }
    public int hebbianArousalModulated() { return hebbianArousalModulated; }
    public int hebbianEdgesSurviving()   { return hebbianEdgesSurviving; }

    public int entityEdgesDecayed()      { return entityEdgesDecayed; }
    public int entityBridgeProtected()   { return entityBridgeProtected; }
    public int entityEdgesSurviving()    { return entityEdgesSurviving; }
    public int crossCapturedEdges()      { return crossCapturedEdges; }

    /** Total edges decayed across both graphs. */
    public int totalEdgesDecayed()       { return hebbianEdgesDecayed + entityEdgesDecayed; }

    /** Total surviving edges across both graphs. */
    public int totalEdgesSurviving()     { return hebbianEdgesSurviving + entityEdgesSurviving; }

    /** Total bridge-protected edges across both graphs. */
    public int totalBridgeProtected()    { return hebbianBridgeProtected + entityBridgeProtected; }

    public int bridgeQ1() { return bridgeQ1; }
    public int bridgeQ2() { return bridgeQ2; }
    public int bridgeQ3() { return bridgeQ3; }
    public int bridgeQ4() { return bridgeQ4; }

    /** Average edge age across all surviving edges, or 0 if none. */
    public float averageEdgeAge() {
        return edgeAgeCount > 0 ? (float) edgeAgeSum / edgeAgeCount : 0f;
    }

    public int maxEdgeAge() { return edgeAgeMax; }

    /** Average importance score, or 0 if none recorded. */
    public float averageImportanceScore() {
        return importanceScoreCount > 0 ? importanceScoreSum / importanceScoreCount : 0f;
    }

    public int hebbianComponents() { return hebbianComponents; }
    public int hebbianActiveNodes() { return hebbianActiveNodes; }

    /** Graph fragmentation ratio: components / activeNodes (1.0 = fully fragmented). */
    public float fragmentationRatio() {
        return hebbianActiveNodes > 0
                ? (float) hebbianComponents / hebbianActiveNodes
                : 0f;
    }

    public int entityMaxDepth()       { return entityMaxDepth; }
    public int depthBucket1()         { return depthBucket1; }
    public int depthBucket2()         { return depthBucket2; }
    public int depthBucket3()         { return depthBucket3; }
    public int depthBucket4Plus()     { return depthBucket4Plus; }

    /** Average BFS depth across all entity pairs, or 0 if none measured. */
    public float averageEntityDepth() {
        return entityDepthCount > 0 ? (float) entityDepthSum / entityDepthCount : 0f;
    }

    /**
     * Records a BFS depth measurement from entity hierarchy analysis.
     *
     * @param depth the hop distance between two entities (1+)
     */
    public void recordEntityDepth(int depth) {
        entityDepthSum += depth;
        entityDepthCount++;
        if (depth > entityMaxDepth) entityMaxDepth = depth;
        switch (depth) {
            case 1 -> depthBucket1++;
            case 2 -> depthBucket2++;
            case 3 -> depthBucket3++;
            default -> { if (depth >= 4) depthBucket4Plus++; }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Internal Helpers
    // ═══════════════════════════════════════════════════════════

    private void recordBridgeQuartile(int bridgeScore) {
        if (bridgeScore < 64)       bridgeQ1++;
        else if (bridgeScore < 128) bridgeQ2++;
        else if (bridgeScore < 192) bridgeQ3++;
        else                        bridgeQ4++;
    }

    private void recordEdgeAge(int age) {
        edgeAgeSum += age;
        edgeAgeCount++;
        if (age > edgeAgeMax) edgeAgeMax = age;
    }

    @Override
    public String toString() {
        return "GraphHealthMetrics{"
                + "hebbian[decayed=" + hebbianEdgesDecayed
                + ", surviving=" + hebbianEdgesSurviving
                + ", bridgeProtected=" + hebbianBridgeProtected
                + ", arousalMod=" + hebbianArousalModulated + "]"
                + ", entity[decayed=" + entityEdgesDecayed
                + ", surviving=" + entityEdgesSurviving
                + ", bridgeProtected=" + entityBridgeProtected + "]"
                + ", bridge[Q1=" + bridgeQ1 + ", Q2=" + bridgeQ2
                + ", Q3=" + bridgeQ3 + ", Q4=" + bridgeQ4 + "]"
                + ", avgAge=" + String.format("%.1f", averageEdgeAge())
                + ", maxAge=" + edgeAgeMax
                + ", avgImportance=" + String.format("%.3f", averageImportanceScore())
                + ", fragmentation=" + String.format("%.3f", fragmentationRatio())
                + ", entityDepth[max=" + entityMaxDepth
                + ", avg=" + String.format("%.1f", averageEntityDepth())
                + ", 1h=" + depthBucket1 + ", 2h=" + depthBucket2
                + ", 3h=" + depthBucket3 + ", 4h+=" + depthBucket4Plus + "]"
                + '}';
    }
}
