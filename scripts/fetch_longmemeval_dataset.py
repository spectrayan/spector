#!/usr/bin/env python3
"""
Preprocess the official full LongMemEval Benchmark Dataset.
Source: d:/git/spector-datasets/longmemeval/original/data/longmemeval_oracle.json (15.3 MB)
        (or longmemeval_s_cleaned.json for 200k scale)
Target: d:/git/spector-datasets/longmemeval/data/

Generates:
- corpus.jsonl (10,866 corpus turn records across multi-session conversations)
- queries.jsonl (500 benchmark evaluation queries across 5 core dimensions)
- qrels.tsv (5,479 ground-truth relevance mappings: query_id -> corpus_id)
- persona.json (Persona metadata definition)
- entities.jsonl (Entity relation definitions)
- temporal_chains.jsonl (Session temporal chain definitions)
- hebbian_edges.jsonl (Co-activation edges)
"""

import json
import os
import sys
import re
from datetime import datetime, timezone

DATASET_SRC_ORACLE = r"D:\git\spector-datasets\longmemeval\original\data\longmemeval_oracle.json"
DATASET_SRC_S = r"D:\git\spector-datasets\longmemeval\original\data\longmemeval_s_cleaned.json"
DATASET_DIR = r"D:\git\spector-datasets\longmemeval\data"

def parse_date_to_ts(date_str: str) -> int:
    """Parse LongMemEval date strings like '2023/04/10 (Mon) 23:07' into timestamp ms."""
    if not date_str:
        return 1700000000000
    try:
        # Strip day of week in parentheses e.g. '(Mon)'
        clean_str = re.sub(r"\([A-Za-z]+\)", "", date_str).strip()
        dt = datetime.strptime(clean_str, "%Y/%m/%d %H:%M")
        return int(dt.replace(tzinfo=timezone.utc).timestamp() * 1000)
    except Exception:
        return 1700000000000

def get_subsystem_for_qtype(q_type: str) -> str:
    """Map LongMemEval question_type to Spector memory subsystem."""
    if "temporal" in q_type.lower():
        return "TEMPORAL_CHAIN"
    elif "update" in q_type.lower():
        return "TEMPORAL_CHAIN"
    elif "multi" in q_type.lower():
        return "HYPERGRAPH"
    else:
        return "HEBBIAN"

def main():
    use_full = "--full" in sys.argv
    src_file = DATASET_SRC_S if use_full and os.path.exists(DATASET_SRC_S) else DATASET_SRC_ORACLE

    if not os.path.exists(src_file):
        print(f"Error: Source dataset not found at {src_file}", file=sys.stderr)
        sys.exit(1)

    os.makedirs(DATASET_DIR, exist_ok=True)
    print(f"Reading LongMemEval source dataset: {src_file}...")

    with open(src_file, "r", encoding="utf-8") as f:
        lme_data = json.load(f)

    corpus_map = {}
    queries = []
    qrels = []
    temporal_chains = {}
    hebbian_edges = []

    for q_idx, item in enumerate(lme_data):
        q_id = item.get("question_id", f"lme_q_{q_idx+1}")
        q_text = item.get("question", "")
        gold_ans = str(item.get("answer", ""))
        q_type = item.get("question_type", "temporal-reasoning")

        ans_sess_ids = set(item.get("answer_session_ids", []))
        subsystem = get_subsystem_for_qtype(q_type)

        query_record = {
            "id": q_id,
            "text": q_text,
            "goldAnswer": gold_ans,
            "cognitiveProfile": "BALANCED",
            "expectedSubsystem": subsystem,
            "cognitiveNdcg": 1.0,
            "baselineNdcg": 0.5
        }
        queries.append(query_record)

        sessions = item.get("haystack_sessions", [])
        session_ids = item.get("haystack_session_ids", [])
        session_dates = item.get("haystack_dates", [])

        for s_idx, turns in enumerate(sessions):
            s_id_raw = session_ids[s_idx] if s_idx < len(session_ids) else f"s_{q_idx}_{s_idx}"
            s_id = re.sub(r"[^a-zA-Z0-9_]", "_", s_id_raw)
            s_date = session_dates[s_idx] if s_idx < len(session_dates) else ""
            ts_ms = parse_date_to_ts(s_date)
            is_ans_sess = s_id_raw in ans_sess_ids or s_id in ans_sess_ids

            if s_id not in temporal_chains:
                temporal_chains[s_id] = []

            for t_idx, turn in enumerate(turns):
                role = turn.get("role", "user")
                text = turn.get("content", "")
                if not text:
                    continue

                corpus_id = f"{s_id}_t{t_idx}"
                temporal_chains[s_id].append(corpus_id)

                if corpus_id not in corpus_map:
                    corpus_map[corpus_id] = {
                        "id": corpus_id,
                        "text": f"{role}: {text}",
                        "title": f"LongMemEval Session {s_id} Turn {t_idx}",
                        "synapticTags": ["longmemeval", s_id, role.lower()],
                        "valence": 0,
                        "importance": 1.0,
                        "arousal": 0,
                        "sessionId": s_id,
                        "timestampMs": ts_ms,
                        "memoryType": "EPISODIC",
                        "agentRecallCount": 0,
                        "entityMentions": []
                    }

                if is_ans_sess and role == "user":
                    qrels.append((q_id, corpus_id))

    # Write output files
    corpus_records = list(corpus_map.values())
    with open(os.path.join(DATASET_DIR, "corpus.jsonl"), "w", encoding="utf-8") as f:
        for rec in corpus_records:
            f.write(json.dumps(rec) + "\n")

    with open(os.path.join(DATASET_DIR, "queries.jsonl"), "w", encoding="utf-8") as f:
        for q in queries:
            f.write(json.dumps(q) + "\n")

    with open(os.path.join(DATASET_DIR, "qrels.tsv"), "w", encoding="utf-8") as f:
        f.write("query_id\tcorpus_id\trelevance\n")
        for q_id, c_id in qrels:
            f.write(f"{q_id}\t{c_id}\t1\n")

    persona = {
        "name": "LongMemEval Benchmark Persona",
        "age": 28,
        "occupation": "Long-Horizon AI Assistant User",
        "interests": ["memory evaluation", "temporal reasoning", "information updates"],
        "lifeContext": "LongMemEval is an official benchmark evaluating long-horizon memory capabilities, temporal reasoning, and information updates across hundreds of multi-session interactions.",
        "personalityTraits": ["organized", "analytical", "adaptable"],
        "companionRelationship": "The AI assistant manages long-horizon session state, multi-session user queries, and updating temporal facts over months of conversation history."
    }
    with open(os.path.join(DATASET_DIR, "persona.json"), "w", encoding="utf-8") as f:
        json.dump(persona, f, indent=2)

    entities = [
        {
            "fromEntity": {"name": "User", "type": "PERSON"},
            "toEntity": {"name": "Assistant", "type": "AGENT"},
            "relationType": "OTHER",
            "sourceMemoryIds": [corpus_records[0]["id"]] if corpus_records else []
        }
    ]
    with open(os.path.join(DATASET_DIR, "entities.jsonl"), "w", encoding="utf-8") as f:
        for ent in entities:
            f.write(json.dumps(ent) + "\n")

    chain_records = [
        {"sessionId": s_id, "orderedMemoryIds": turn_ids}
        for s_id, turn_ids in temporal_chains.items()
        if turn_ids
    ]
    with open(os.path.join(DATASET_DIR, "temporal_chains.jsonl"), "w", encoding="utf-8") as f:
        for tc in chain_records:
            f.write(json.dumps(tc) + "\n")

    # Generate Hebbian edges for adjacent turns in sessions
    for turn_ids in temporal_chains.values():
        if len(turn_ids) >= 2:
            for i in range(len(turn_ids) - 1):
                hebbian_edges.append({
                    "memoryIdA": turn_ids[i],
                    "memoryIdB": turn_ids[i+1],
                    "coActivationCount": 2
                })

    with open(os.path.join(DATASET_DIR, "hebbian_edges.jsonl"), "w", encoding="utf-8") as f:
        for edge in hebbian_edges[:1000]: # Cap to first 1000 edges for clean loading
            f.write(json.dumps(edge) + "\n")

    print(f"=== LongMemEval Full Dataset Preprocessing Complete ===")
    print(f"Corpus Records: {len(corpus_records)}")
    print(f"Queries:        {len(queries)}")
    print(f"Qrels Mappings: {len(qrels)}")
    print(f"Temporal Chains:{len(chain_records)}")
    print(f"Output Path:    {DATASET_DIR}")

if __name__ == "__main__":
    main()
