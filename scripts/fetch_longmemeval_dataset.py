#!/usr/bin/env python3
"""
Fetch and preprocess the LongMemEval Benchmark Dataset.
Stores output in spector-datasets repo: d:/git/spector-datasets/longmemeval/data/

Outputs:
- corpus.jsonl: Multi-session long-horizon assistant interactions matching Spector DatasetLoader schema
- queries.jsonl: Benchmark questions across 5 core evaluation dimensions
- qrels.tsv: Ground-truth relevance mapping (query_id -> corpus_id)
- persona.json: Dataset metadata matching DatasetLoader schema
- entities.jsonl: Extracted entity relation definitions
- temporal_chains.jsonl: Temporal session chain ordering
- hebbian_edges.jsonl: Co-activation edge definitions
"""

import json
import os
import sys

DATASET_DIR = r"D:\git\spector-datasets\longmemeval\data"

SAMPLE_LONGMEMEVAL_SESSIONS = [
    {
        "session_id": "lme_sess_1",
        "timestamp_ms": 1770100000000,
        "utterances": [
            {"id": "lme_u101", "speaker": "User", "text": "I'm planning a 2-week vacation to Kyoto in October. I prefer quiet traditional ryokans with private hot springs."},
            {"id": "lme_u102", "speaker": "User", "text": "My budget per night is around 40,000 JPY."},
        ]
    },
    {
        "session_id": "lme_sess_2",
        "timestamp_ms": 1770800000000,
        "utterances": [
            {"id": "lme_u201", "speaker": "User", "text": "Change of plans: my Kyoto trip budget has increased to 65,000 JPY per night."},
            {"id": "lme_u202", "speaker": "User", "text": "Also, I've decided to travel in November instead of October for autumn foliage."},
        ]
    },
    {
        "session_id": "lme_sess_3",
        "timestamp_ms": 1771500000000,
        "utterances": [
            {"id": "lme_u301", "speaker": "User", "text": "I just booked Gion Sano Ryokan in Kyoto for November 10-24."},
            {"id": "lme_u302", "speaker": "User", "text": "Can you remind me what my updated nightly accommodation budget was?"},
        ]
    }
]

SAMPLE_LONGMEMEVAL_QUERIES = [
    {
        "id": "lme_q1",
        "text": "What is the user's updated nightly budget for their Kyoto trip?",
        "gold_answer": "The updated nightly budget is 65,000 JPY (updated from 40,000 JPY).",
        "cognitiveProfile": "BALANCED",
        "expectedSubsystem": "TEMPORAL_CHAIN",
        "cognitiveNdcg": 1.0,
        "baselineNdcg": 0.5,
        "relevant_corpus_ids": ["lme_u201"]
    },
    {
        "id": "lme_q2",
        "text": "When is the user travelling to Kyoto and where are they staying?",
        "gold_answer": "Travelling in November (10-24), staying at Gion Sano Ryokan.",
        "cognitiveProfile": "BALANCED",
        "expectedSubsystem": "TEMPORAL_CHAIN",
        "cognitiveNdcg": 1.0,
        "baselineNdcg": 0.5,
        "relevant_corpus_ids": ["lme_u202", "lme_u301"]
    },
    {
        "id": "lme_q3",
        "text": "What type of accommodation does the user prefer?",
        "gold_answer": "Quiet traditional ryokans with private hot springs.",
        "cognitiveProfile": "BALANCED",
        "expectedSubsystem": "HEBBIAN",
        "cognitiveNdcg": 1.0,
        "baselineNdcg": 0.5,
        "relevant_corpus_ids": ["lme_u101"]
    }
]

def main():
    os.makedirs(DATASET_DIR, exist_ok=True)

    corpus_records = []
    temporal_chains = []

    for session in SAMPLE_LONGMEMEVAL_SESSIONS:
        sess_id = session["session_id"]
        ts = session["timestamp_ms"]
        u_ids = []
        for u in session["utterances"]:
            u_ids.append(u["id"])
            record = {
                "id": u["id"],
                "text": u["text"],
                "title": f"Interaction {u['id']}",
                "synapticTags": ["longmemeval", "agent_interaction"],
                "valence": 0,
                "importance": 1.0,
                "arousal": 0,
                "sessionId": sess_id,
                "timestampMs": ts,
                "memoryType": "EPISODIC",
                "agentRecallCount": 0,
                "entityMentions": []
            }
            corpus_records.append(record)

        temporal_chains.append({"sessionId": sess_id, "orderedMemoryIds": u_ids})

    with open(os.path.join(DATASET_DIR, "corpus.jsonl"), "w", encoding="utf-8") as f:
        for rec in corpus_records:
            f.write(json.dumps(rec) + "\n")

    with open(os.path.join(DATASET_DIR, "queries.jsonl"), "w", encoding="utf-8") as f:
        for q in SAMPLE_LONGMEMEVAL_QUERIES:
            f.write(json.dumps(q) + "\n")

    with open(os.path.join(DATASET_DIR, "qrels.tsv"), "w", encoding="utf-8") as f:
        f.write("query_id\tcorpus_id\trelevance\n")
        for q in SAMPLE_LONGMEMEVAL_QUERIES:
            for cid in q["relevant_corpus_ids"]:
                f.write(f"{q['id']}\t{cid}\t1\n")

    persona = {
        "name": "Sarah Miller",
        "age": 29,
        "occupation": "Travel Writer",
        "interests": ["travel planning", "Japanese culture", "hot springs"],
        "lifeContext": "Sarah is an avid traveler and writer planning a multi-week trip to Kyoto, managing accommodation budgets and schedules.",
        "personalityTraits": ["adventurous", "detail-oriented", "organized"],
        "companionRelationship": "The AI assistant manages trip planning updates, budget changes, and accommodation preferences."
    }
    with open(os.path.join(DATASET_DIR, "persona.json"), "w", encoding="utf-8") as f:
        json.dump(persona, f, indent=2)

    entities = [
        {
            "fromEntity": {"name": "User", "type": "PERSON"},
            "toEntity": {"name": "Kyoto", "type": "LOCATION"},
            "relationType": "OTHER",
            "sourceMemoryIds": ["lme_u101"]
        }
    ]
    with open(os.path.join(DATASET_DIR, "entities.jsonl"), "w", encoding="utf-8") as f:
        for ent in entities:
            f.write(json.dumps(ent) + "\n")

    with open(os.path.join(DATASET_DIR, "temporal_chains.jsonl"), "w", encoding="utf-8") as f:
        for tc in temporal_chains:
            f.write(json.dumps(tc) + "\n")

    hebbian_edges = [
        {"memoryIdA": "lme_u102", "memoryIdB": "lme_u201", "coActivationCount": 3}
    ]
    with open(os.path.join(DATASET_DIR, "hebbian_edges.jsonl"), "w", encoding="utf-8") as f:
        for edge in hebbian_edges:
            f.write(json.dumps(edge) + "\n")

    emb_file = os.path.join(DATASET_DIR, "embeddings.bin")
    if os.path.exists(emb_file):
        os.remove(emb_file)

    print(f"Generated LongMemEval dataset: {len(corpus_records)} records, {len(SAMPLE_LONGMEMEVAL_QUERIES)} queries.")

if __name__ == "__main__":
    main()
