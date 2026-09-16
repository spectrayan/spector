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
    public static final String SPACETIME_SCORING     = "spacetime_scoring";
    public static final String SCORING               = "scoring";
    public static final String GRAPH_EXPANSION       = "graph_expansion";
    public static final String EVIDENCE_FUSION       = "evidence_fusion";
    public static final String LATERAL_INHIBITION    = "lateral_inhibition";
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
    public static final String SPECTRAL_SPARSIFICATION   = "spectral_sparsification";
    public static final String MANIFOLD_CONSOLIDATION    = "manifold_consolidation";
    public static final String SOFT_IDENTITY_ANCHOR      = "soft_identity_anchor";
    public static final String WAL_JOURNAL               = "wal_journal";

    // Active Inference Self-Model Engine (AISME) Relays
    public static final String HOMEOSTATIC_BIAS          = "homeostatic_bias";
    public static final String FREE_ENERGY_GUIDED        = "free_energy_guided";
    public static final String HOPFIELD_ASSOCIATIVE      = "hopfield_associative";
    public static final String MANIFOLD_RERANK           = "manifold_rerank";
    public static final String CONSTRUCTIVE_SIMULATION   = "constructive_simulation";
    public static final String CONSCIOUSNESS_CONTINUITY  = "consciousness_continuity";
    public static final String CONSCIOUS_ACCESS          = "conscious_access";
    public static final String CONSTRUCTIVE_PERSISTENCE  = "constructive_persistence";
    public static final String EPISTEMIC_LEARNING         = "epistemic_learning";
    public static final String EVENT_DENSITY_GATING              = "event_density_gating";
    public static final String SURPRISAL_BOUNDARY_SEGMENTATION  = "surprisal_boundary_segmentation";
    public static final String EDGE_ANONYMIZATION               = "edge_anonymization";
    public static final String DIFFERENTIAL_PRIVACY             = "differential_privacy";
    public static final String COMPOSITE_IMPORTANCE             = "composite_importance";
    public static final String LIFESPAN_ADAPTIVE_PRUNING        = "lifespan_adaptive_pruning";

    // Spacetime Simulation Relays (ADR-0031)
    public static final String SPACETIME_SEED                   = "spacetime_seed";

    // Dream Pathway Relays (ADR-0035 §7.3, ADR-0036 §14 Dream row).
    // Values must match the names DreamPathway used before DreamRecipe was extracted:
    // relay names are load-bearing for traces, outcome scopes and the parity gate.
    public static final String DREAM_GATE                       = "dream_gate";
    public static final String SALIENT_SEED                     = "salient_seed";
    public static final String FRAGMENT_UNPACK                  = "fragment_unpack";
    public static final String HYPER_ASSOCIATE                  = "hyper_associate";
    public static final String REM_REPLAY                       = "rem_replay";
    public static final String SCENE_CONSTRUCT                  = "scene_construct";
    public static final String COUNTERFACTUAL_PROBE             = "counterfactual_probe";
    public static final String LANGEVIN_DISCOVERY               = "langevin_discovery";
    public static final String EFE_TRIAGE                       = "efe_triage";
    public static final String CONCEPT_EXTRACT                  = "concept_extract";
    public static final String DREAM_JOURNAL                    = "dream_journal";
    public static final String DREAM_INGESTION                  = "dream_ingestion";

    // Wander Pathway Relays (ADR-0036 §14 "Decide / Wander / Express" row: all DEGRADE).
    public static final String IDLE_GATE                        = "idle_gate";
    public static final String AUTOBIOGRAPHICAL_SAMPLING        = "autobiographical_sampling";
    public static final String HOPFIELD_MIND_WANDERING          = "hopfield_mind_wandering";
    public static final String MANIFOLD_SYNERGY                 = "manifold_synergy";
    public static final String HEBBIAN_REINFORCEMENT            = "hebbian_reinforcement";
    public static final String LONGITUDINAL_CONTINUITY          = "longitudinal_continuity";

    // Express Pathway Relays. These are CamelCase where the rest of the codebase is
    // snake_case; preserved verbatim because relay names appear in traces and outcome scopes.
    public static final String IDIOLECT_STYLOMETRY              = "IdiolectStylometry";
    public static final String VOCAL_PROSODY                    = "VocalProsody";
    public static final String EMBODIED_KINESICS                = "EmbodiedKinesics";
    public static final String PHENOMENOLOGICAL_STREAM          = "PhenomenologicalStream";

    // Decide Pathway Relays.
    public static final String POLICY_INFERENCE                 = "policy_inference";
    public static final String EXPERIMENT_THOUGHT               = "experiment_thought";
}
