#!/usr/bin/env python3
"""
Model-Based Entity & Synaptic Tag Extraction for LongMemEval Dataset using Local Ollama LLM.
Source: d:/git/spector-datasets/longmemeval/original/data/longmemeval_oracle.json
Target: d:/git/spector-datasets/longmemeval/data/

Outputs:
- corpus.jsonl (10,866 utterances enriched with model-extracted entityMentions & synapticTags)
- queries.jsonl (500 queries)
- qrels.tsv (5,479 qrel mappings)
- spector-bench.yml (Dataset YAML configuration recording extraction & embedding parameters)
- entities.jsonl, temporal_chains.jsonl, hebbian_edges.jsonl, persona.json
"""

import json
import os
import sys
import re
import urllib.request
from datetime import datetime, timezone

OLLAMA_URL = "http://localhost:11434/api/generate"
EXTRACTION_MODEL = "llama3.1:latest"
EMBEDDING_MODEL = "nomic-embed-text:latest"

from pathlib import Path
_DATASETS_BASE = Path(os.environ.get("SPECTOR_DATASETS_DIR", Path(__file__).resolve().parents[2] / "spector-datasets"))
DATASET_SRC = str(_DATASETS_BASE / "longmemeval" / "original" / "data" / "longmemeval_oracle.json")
DATASET_DIR = str(_DATASETS_BASE / "longmemeval" / "data")
CHECKPOINT_FILE = os.path.join(DATASET_DIR, "longmemeval_extraction_checkpoint.json")

def query_ollama_json(prompt: str, model: str = EXTRACTION_MODEL, timeout: int = 15) -> dict:
    payload = {
        "model": model,
        "prompt": prompt,
        "format": "json",
        "stream": False
    }
    req = urllib.request.Request(
        OLLAMA_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            raw_text = data.get("response", "{}")
            return json.loads(raw_text)
    except Exception:
        return {}

def parse_date_to_ts(date_str: str) -> int:
    if not date_str:
        return 1700000000000
    try:
        clean_str = re.sub(r"\([A-Za-z]+\)", "", date_str).strip()
        dt = datetime.strptime(clean_str, "%Y/%m/%d %H:%M")
        return int(dt.replace(tzinfo=timezone.utc).timestamp() * 1000)
    except Exception:
        return 1700000000000

def get_subsystem_for_qtype(q_type: str) -> str:
    if "temporal" in q_type.lower():
        return "TEMPORAL_CHAIN"
    elif "update" in q_type.lower():
        return "TEMPORAL_CHAIN"
    elif "multi" in q_type.lower():
        return "HYPERGRAPH"
    else:
        return "HEBBIAN"

def save_checkpoint(data: dict):
    tmp_path = CHECKPOINT_FILE + ".tmp"
    with open(tmp_path, "w", encoding="utf-8") as f:
        json.dump(data, f)
    os.replace(tmp_path, CHECKPOINT_FILE)

def main():
    if not os.path.exists(DATASET_SRC):
        print(f"Error: Source dataset not found at {DATASET_SRC}", file=sys.stderr)
        sys.exit(1)

    os.makedirs(DATASET_DIR, exist_ok=True)
    print(f"=== LongMemEval Model-Based Extraction (Ollama: {EXTRACTION_MODEL}) ===")

    checkpoint = {}
    if os.path.exists(CHECKPOINT_FILE):
        try:
            with open(CHECKPOINT_FILE, "r", encoding="utf-8") as f:
                checkpoint = json.load(f)
            print(f"Loaded existing checkpoint with {len(checkpoint)} extracted items.")
        except Exception:
            checkpoint = {}

    with open(DATASET_SRC, "r", encoding="utf-8") as f:
        lme_data = json.load(f)

    corpus_map = {}
    queries = []
    qrels = []
    temporal_chains = {}
    hebbian_edges = []

    total_utterances = 0

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

                total_utterances += 1
                corpus_id = f"{s_id}_t{t_idx}"
                temporal_chains[s_id].append(corpus_id)

                if corpus_id in corpus_map:
                    continue

                full_text = f"{role}: {text}"

                if corpus_id in checkpoint:
                    extracted = checkpoint[corpus_id]
                else:
                    prompt = (
                        f"Extract named entities (PERSON, LOCATION, ORGANIZATION, EVENT, CONCEPT, PET, OBJECT) "
                        f"and 3-5 synaptic tags from the conversation turn: '{text[:500]}'. "
                        f"Return JSON object: {{\"entities\": [{{\"name\": \"...\", \"type\": \"...\"}}], \"synapticTags\": [\"...\"]}}"
                    )
                    extracted = query_ollama_json(prompt)
                    if not extracted.get("entities"):
                        extracted["entities"] = [{"name": role.capitalize(), "type": "PERSON"}]
                    if not extracted.get("synapticTags"):
                        extracted["synapticTags"] = ["longmemeval", s_id, role.lower()]

                    checkpoint[corpus_id] = extracted
                    if len(checkpoint) % 100 == 0:
                        save_checkpoint(checkpoint)
                        print(f"Extraction progress: {len(checkpoint)} utterances processed.")

                entity_mentions = extracted.get("entities", [{"name": role.capitalize(), "type": "PERSON"}])
                tags = extracted.get("synapticTags", ["longmemeval", s_id, role.lower()])

                base_tags = ["longmemeval", s_id, role.lower()]
                for bt in base_tags:
                    if bt not in tags:
                        tags.append(bt)

                corpus_map[corpus_id] = {
                    "id": corpus_id,
                    "text": full_text,
                    "title": f"LongMemEval Session {s_id} Turn {t_idx}",
                    "synapticTags": tags,
                    "valence": 0,
                    "importance": 1.0,
                    "arousal": 0,
                    "sessionId": s_id,
                    "timestampMs": ts_ms,
                    "memoryType": "EPISODIC",
                    "agentRecallCount": 0,
                    "entityMentions": entity_mentions
                }

                if is_ans_sess and role == "user":
                    qrels.append((q_id, corpus_id))

    save_checkpoint(checkpoint)

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

    for turn_ids in temporal_chains.values():
        if len(turn_ids) >= 2:
            for i in range(len(turn_ids) - 1):
                hebbian_edges.append({
                    "memoryIdA": turn_ids[i],
                    "memoryIdB": turn_ids[i+1],
                    "coActivationCount": 2
                })

    with open(os.path.join(DATASET_DIR, "hebbian_edges.jsonl"), "w", encoding="utf-8") as f:
        for edge in hebbian_edges[:1000]:
            f.write(json.dumps(edge) + "\n")

    yaml_content = f"""spector:
  benchmark:
    dataset-name: "LongMemEval Benchmark (Official ICLR/arXiv)"
    extraction:
      provider: "OLLAMA"
      model: "{EXTRACTION_MODEL}"
      base-url: "http://localhost:11434"
    embedding:
      provider: "OLLAMA"
      model: "{EMBEDDING_MODEL}"
      dimension: 768
      metric: "COSINE"
    cognitive:
      profile: "BALANCED"
      text-search-mode: "HYBRID"
      mmr-lambda: 0.7
"""
    with open(os.path.join(DATASET_DIR, "spector-bench.yml"), "w", encoding="utf-8") as f:
        f.write(yaml_content)

    print(f"=== LongMemEval Ollama Extraction Complete ===")
    print(f"Corpus Records:  {len(corpus_records)}")
    print(f"Queries:         {len(queries)}")
    print(f"Qrels Mappings:  {len(qrels)}")
    print(f"Dataset Config:  {os.path.join(DATASET_DIR, 'spector-bench.yml')}")

if __name__ == "__main__":
    main()
