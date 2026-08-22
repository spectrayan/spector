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
package com.spectrayan.spector.memory.pathway;

/**
 * Constants class containing all relay names for pathways.
 */
public final class RelayNames {

    private RelayNames() {}

    // Recall Pathway Relays
    public static final String TRANSDUCTION          = "transduction";
    public static final String PROSPECTIVE           = "prospective";
    public static final String GOVERNED_RELEASE_GATE = "governed_release_gate";
    public static final String VECTOR_SEARCH         = "vector_search";
    public static final String SCORING               = "scoring";
    public static final String GRAPH_EXPANSION       = "graph_expansion";
    public static final String EVIDENCE_FUSION       = "evidence_fusion";
    public static final String BM25_SEARCH           = "bm25_search";
    public static final String RRF_RESCORE           = "rrf_rescore";
    public static final String SORT_TRUNCATE         = "sort_truncate";
    public static final String COLBERT_RERANK        = "colbert_rerank";
    public static final String MMR_RERANK            = "mmr_rerank";
    public static final String TEMPERATURE           = "temperature";
    public static final String CONSOLIDATION         = "consolidation";

    // Divergent Branch Relays (Tier Scans)
    public static final String TIER_HOT          = "hot";
    public static final String TIER_WARM         = "warm";
    public static final String TIER_COLD         = "cold";
    public static final String TIER_PROCEDURAL   = "proc";

    // Ingestion Pathway Relays
    public static final String DEDUP_GUARD           = "dedup_guard";
    public static final String TAG_TRANSDUCTION      = "tag_transduction";
    public static final String DOPAMINERGIC_SURPRISE = "dopaminergic_surprise";
    public static final String SCALAR_QUANTIZATION   = "scalar_quantization";
    public static final String HEADER_ASSEMBLY       = "header_assembly";
    public static final String CORTICAL_WRITE        = "cortical_write";
    public static final String POST_INGEST_SYNC      = "post_ingest_sync";
    public static final String GRAPH_LINKING         = "graph_linking";
    public static final String KG_ENRICHMENT         = "kg_enrichment";

    // Reflect Pathway Relays
    public static final String SYNAPTIC_PRUNING          = "synaptic_pruning";
    public static final String EPISODIC_CONSOLIDATION    = "episodic_consolidation";
    public static final String SOUL_DRIFT_REFUSION       = "soul_drift_refusion";
    public static final String PROCEDURAL_CRYSTALLIZATION = "procedural_crystallization";
    public static final String PROACTIVE_INTERFERENCE    = "proactive_interference";
    public static final String HEBBIAN_HOMEOSTASIS       = "hebbian_homeostasis";
    public static final String TEMPORAL_PRUNING          = "temporal_pruning";
    public static final String CROSS_LAYER_PROMOTION     = "cross_layer_promotion";
    public static final String ENTITY_MAINTENANCE        = "entity_maintenance";
    public static final String MANIFOLD_CONSOLIDATION    = "manifold_consolidation";
    public static final String WAL_JOURNAL               = "wal_journal";

    // Active Inference Self-Model Engine (AISME) Relays
    public static final String HOMEOSTATIC_BIAS          = "homeostatic_bias";
    public static final String FREE_ENERGY_GUIDED        = "free_energy_guided";
    public static final String HOPFIELD_ASSOCIATIVE      = "hopfield_associative";
    public static final String MANIFOLD_RERANK           = "manifold_rerank";
    public static final String CONSTRUCTIVE_SIMULATION   = "constructive_simulation";
    public static final String CONSCIOUSNESS_CONTINUITY  = "consciousness_continuity";
    public static final String CONSCIOUS_ACCESS          = "conscious_access";
}
