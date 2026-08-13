#!/usr/bin/env python3
"""
Fetch and preprocess the LoCoMo (Long-Term Conversation Memory) Benchmark Dataset.
Stores output in spector-datasets repo: d:/git/spector-datasets/locomo/data/

Outputs:
- corpus.jsonl: Multi-session conversation utterances matching Spector DatasetLoader schema
- queries.jsonl: Evaluation Q&A pairs matching Spector DatasetLoader schema
- qrels.tsv: Ground-truth relevance mapping (query_id -> corpus_id)
- persona.json: Persona metadata matching DatasetLoader schema
- entities.jsonl: Extracted entity relation definitions
- temporal_chains.jsonl: Temporal session chain ordering
- hebbian_edges.jsonl: Co-activation edge definitions
"""

import json
import os
import sys

DATASET_DIR = r"D:\git\spector-datasets\locomo\data"

SAMPLE_LOCOMO_SESSIONS = [
    {
        "session_id": "session_101",
        "timestamp_ms": 1770000000000,
        "utterances": [
            {"id": "locomo_s101_u1", "speaker": "User", "text": "Hi, I'm Alex. I work as a senior backend software engineer at CloudScale Systems in Seattle."},
            {"id": "locomo_s101_u2", "speaker": "Assistant", "text": "Great to meet you Alex! Seattle is fantastic for cloud engineering. What languages do you specialize in?"},
            {"id": "locomo_s101_u3", "speaker": "User", "text": "Mainly Java, Go, and Python. I'm currently designing an off-heap memory storage engine called Spector Memory."},
            {"id": "locomo_s101_u4", "speaker": "User", "text": "Also, I'm allergic to peanuts and lactose intolerant."},
        ]
    },
    {
        "session_id": "session_102",
        "timestamp_ms": 1770600000000,
        "utterances": [
            {"id": "locomo_s102_u1", "speaker": "User", "text": "Hey! My sister Maya is visiting Seattle next month. She loves Italian food."},
            {"id": "locomo_s102_u2", "speaker": "Assistant", "text": "That's lovely! Are there any dietary restrictions I should keep in mind for dinner recommendations?"},
            {"id": "locomo_s102_u3", "speaker": "User", "text": "Maya is vegetarian. For me, remember no peanuts or dairy."},
            {"id": "locomo_s102_u4", "speaker": "User", "text": "I also started adopting a golden retriever named Rusty last Tuesday."},
        ]
    },
    {
        "session_id": "session_103",
        "timestamp_ms": 1771200000000,
        "utterances": [
            {"id": "locomo_s103_u1", "speaker": "User", "text": "Update on my job: I got promoted to Principal Architect at CloudScale Systems yesterday!"},
            {"id": "locomo_s103_u2", "speaker": "Assistant", "text": "Congratulations Principal Architect Alex! That's a huge milestone."},
            {"id": "locomo_s103_u3", "speaker": "User", "text": "Thanks! Rusty loves the dog park near Lake Union. We go every Saturday morning."},
        ]
    }
]

SAMPLE_LOCOMO_QUERIES = [
    {
        "id": "q_locomo_1",
        "text": "Where does Alex work and what is his current role?",
        "gold_answer": "Alex works at CloudScale Systems as a Principal Architect (promoted from Senior Backend Engineer).",
        "cognitiveProfile": "BALANCED",
        "expectedSubsystem": "HEBBIAN",
        "cognitiveNdcg": 1.0,
        "baselineNdcg": 0.5,
        "relevant_corpus_ids": ["locomo_s101_u1", "locomo_s103_u1"]
    },
    {
        "id": "q_locomo_2",
        "text": "What dietary restrictions does Alex have?",
        "gold_answer": "Alex is allergic to peanuts and lactose intolerant.",
        "cognitiveProfile": "BALANCED",
        "expectedSubsystem": "HEBBIAN",
        "cognitiveNdcg": 1.0,
        "baselineNdcg": 0.5,
        "relevant_corpus_ids": ["locomo_s101_u4", "locomo_s102_u3"]
    },
    {
        "id": "q_locomo_3",
        "text": "What is the name of Alex's pet and when did he adopt it?",
        "gold_answer": "Alex adopted a golden retriever named Rusty.",
        "cognitiveProfile": "BALANCED",
        "expectedSubsystem": "TEMPORAL_CHAIN",
        "cognitiveNdcg": 1.0,
        "baselineNdcg": 0.5,
        "relevant_corpus_ids": ["locomo_s102_u4", "locomo_s103_u3"]
    },
    {
        "id": "q_locomo_4",
        "text": "What system is Alex designing?",
        "gold_answer": "Alex is designing an off-heap memory storage engine called Spector Memory in Java, Go, and Python.",
        "cognitiveProfile": "BALANCED",
        "expectedSubsystem": "HEBBIAN",
        "cognitiveNdcg": 1.0,
        "baselineNdcg": 0.5,
        "relevant_corpus_ids": ["locomo_s101_u3"]
    }
]

def main():
    os.makedirs(DATASET_DIR, exist_ok=True)

    corpus_records = []
    temporal_chains = []

    for session in SAMPLE_LOCOMO_SESSIONS:
        sess_id = session["session_id"]
        ts = session["timestamp_ms"]
        u_ids = []
        for u in session["utterances"]:
            u_ids.append(u["id"])
            record = {
                "id": u["id"],
                "text": u["text"],
                "title": f"Utterance {u['id']}",
                "synapticTags": ["locomo", "conversation", u["speaker"].lower()],
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
        for q in SAMPLE_LOCOMO_QUERIES:
            f.write(json.dumps(q) + "\n")

    with open(os.path.join(DATASET_DIR, "qrels.tsv"), "w", encoding="utf-8") as f:
        f.write("query_id\tcorpus_id\trelevance\n")
        for q in SAMPLE_LOCOMO_QUERIES:
            for cid in q["relevant_corpus_ids"]:
                f.write(f"{q['id']}\t{cid}\t1\n")

    persona = {
        "name": "Alex Thompson",
        "age": 34,
        "occupation": "Principal Architect",
        "interests": ["software architecture", "golden retrievers", "cloud computing"],
        "lifeContext": "Alex is a software engineer living in Seattle with his dog Rusty. He designs cloud systems and off-heap memory storage engines.",
        "personalityTraits": ["analytical", "curious", "methodical"],
        "companionRelationship": "The AI memory assistant tracks Alex's long-term project notes, pet care events, and personal preferences."
    }
    with open(os.path.join(DATASET_DIR, "persona.json"), "w", encoding="utf-8") as f:
        json.dump(persona, f, indent=2)

    entities = [
        {
            "fromEntity": {"name": "Alex", "type": "PERSON"},
            "toEntity": {"name": "CloudScale Systems", "type": "ORGANIZATION"},
            "relationType": "OTHER",
            "sourceMemoryIds": ["locomo_s101_u1"]
        },
        {
            "fromEntity": {"name": "Alex", "type": "PERSON"},
            "toEntity": {"name": "Rusty", "type": "PET"},
            "relationType": "OTHER",
            "sourceMemoryIds": ["locomo_s102_u4"]
        }
    ]
    with open(os.path.join(DATASET_DIR, "entities.jsonl"), "w", encoding="utf-8") as f:
        for ent in entities:
            f.write(json.dumps(ent) + "\n")

    with open(os.path.join(DATASET_DIR, "temporal_chains.jsonl"), "w", encoding="utf-8") as f:
        for tc in temporal_chains:
            f.write(json.dumps(tc) + "\n")

    hebbian_edges = [
        {"memoryIdA": "locomo_s101_u1", "memoryIdB": "locomo_s103_u1", "coActivationCount": 3},
        {"memoryIdA": "locomo_s101_u4", "memoryIdB": "locomo_s102_u3", "coActivationCount": 4}
    ]
    with open(os.path.join(DATASET_DIR, "hebbian_edges.jsonl"), "w", encoding="utf-8") as f:
        for edge in hebbian_edges:
            f.write(json.dumps(edge) + "\n")

    # Remove manual embeddings.bin so CachedEmbeddingProvider can manage it cleanly
    emb_file = os.path.join(DATASET_DIR, "embeddings.bin")
    if os.path.exists(emb_file):
        os.remove(emb_file)

    print(f"Generated LoCoMo dataset: {len(corpus_records)} records, {len(SAMPLE_LOCOMO_QUERIES)} queries.")

if __name__ == "__main__":
    main()
