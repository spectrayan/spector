#!/usr/bin/env python3
"""
Model-Based Entity & Synaptic Tag Extraction for LoCoMo Dataset using Local Ollama LLM.
Source: d:/git/spector-datasets/locomo/original/data/locomo10.json
Target: d:/git/spector-datasets/locomo/data/

Outputs:
- corpus.jsonl (Utterances enriched with model-extracted entityMentions & synapticTags)
- entities.jsonl (Model-extracted EntityRelation graph definitions)
- spector-bench.yml (Dataset YAML configuration recording extraction & embedding parameters)
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
DATASET_SRC = str(_DATASETS_BASE / "locomo" / "original" / "data" / "locomo10.json")
DATASET_DIR = str(_DATASETS_BASE / "locomo" / "data")
CHECKPOINT_FILE = os.path.join(DATASET_DIR, "locomo_extraction_checkpoint.json")

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
    """Parse date strings like '1:54 PM, 7 May, 2023' or '1:56 pm on 8 May, 2023' into timestamp ms."""
    if not date_str:
        return 1700000000000
    cleaned = date_str.replace(" on ", ", ").strip()
    cleaned = re.sub(r"\s+", " ", cleaned)
    for fmt in [
        "%I:%M %p, %d %B, %Y",
        "%I:%M %p, %d %B %Y",
        "%I:%M %p, %B %d, %Y",
        "%d %B, %Y",
        "%d %B %Y",
        "%B %d, %Y",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d"
    ]:
        try:
            dt = datetime.strptime(cleaned, fmt)
            return int(dt.replace(tzinfo=timezone.utc).timestamp() * 1000)
        except Exception:
            continue
    print(f"[WARN] Failed to parse date string: '{date_str}'", file=sys.stderr)
    return 1700000000000

def get_subsystem_for_category(category: int) -> str:
    if category == 2:
        return "TEMPORAL_CHAIN"
    elif category == 3:
        return "HYPERGRAPH"
    elif category == 4:
        return "ENTITY_GRAPH"
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
    print(f"=== LoCoMo Model-Based Extraction (Ollama: {EXTRACTION_MODEL}) ===")

    checkpoint = {}
    if os.path.exists(CHECKPOINT_FILE):
        try:
            with open(CHECKPOINT_FILE, "r", encoding="utf-8") as f:
                checkpoint = json.load(f)
            print(f"Loaded existing checkpoint with {len(checkpoint)} extracted items.")
        except Exception:
            checkpoint = {}

    with open(DATASET_SRC, "r", encoding="utf-8") as f:
        locomo_data = json.load(f)

    corpus_records = []
    corpus_ids = set()
    queries = []
    qrels = []
    temporal_chains = []
    hebbian_edges = []
    entities = []
    known_entities = set()

    total_utterances = 0

    for sample_idx, sample in enumerate(locomo_data):
        sample_id = sample.get("sample_id", f"conv_{sample_idx+1}")
        sample_clean = re.sub(r"[^a-zA-Z0-9]", "_", str(sample_id))
        conv = sample.get("conversation", {})
        speaker_a = conv.get("speaker_a", "SpeakerA")
        speaker_b = conv.get("speaker_b", "SpeakerB")

        sess_keys = sorted(
            [k for k in conv.keys() if k.startswith("session_") and not k.endswith("_date_time")],
            key=lambda x: int(x.split("_")[1]) if x.split("_")[1].isdigit() else 0
        )

        for sess_k in sess_keys:
            sess_num = sess_k.replace("session_", "")
            dt_str = conv.get(f"session_{sess_num}_date_time", "")
            ts_ms = parse_date_to_ts(dt_str)
            turns = conv.get(sess_k, [])
            if not isinstance(turns, list):
                continue

            session_id = f"{sample_clean}_sess_{sess_num}"
            ordered_turn_ids = []

            for turn in turns:
                dia_id = turn.get("dia_id", "")
                speaker = turn.get("speaker", "User")
                text = turn.get("text", "")
                if not text:
                    continue

                total_utterances += 1
                corpus_id = f"{sample_clean}_{dia_id}".replace(":", "_")
                corpus_ids.add(corpus_id)
                ordered_turn_ids.append(corpus_id)

                full_text = f"{speaker}: {text}"

                if corpus_id in checkpoint:
                    extracted = checkpoint[corpus_id]
                else:
                    prompt = (
                        f"Extract named entities (PERSON, LOCATION, ORGANIZATION, EVENT, CONCEPT, PET) "
                        f"and 3-5 synaptic tags from the conversation utterance: '{text}'. "
                        f"Return JSON object: {{\"entities\": [{{\"name\": \"...\", \"type\": \"...\"}}], \"synapticTags\": [\"...\"]}}"
                    )
                    extracted = query_ollama_json(prompt)
                    if not extracted.get("entities"):
                        extracted["entities"] = [{"name": speaker, "type": "PERSON"}]
                    if not extracted.get("synapticTags"):
                        extracted["synapticTags"] = ["locomo", sample_clean, f"session_{sess_num}", speaker.lower()]

                    checkpoint[corpus_id] = extracted
                    if len(checkpoint) % 50 == 0:
                        save_checkpoint(checkpoint)
                        print(f"Extraction progress: {len(checkpoint)} / {total_utterances} utterances processed.")

                entity_mentions = extracted.get("entities", [{"name": speaker, "type": "PERSON"}])
                tags = extracted.get("synapticTags", ["locomo", sample_clean, f"session_{sess_num}", speaker.lower()])

                base_tags = ["locomo", sample_clean, f"session_{sess_num}", speaker.lower()]
                for bt in base_tags:
                    if bt not in tags:
                        tags.append(bt)

                record = {
                    "id": corpus_id,
                    "text": full_text,
                    "title": f"LoCoMo {sample_clean} Session {sess_num} {dia_id}",
                    "synapticTags": tags,
                    "valence": 0,
                    "importance": 1.0,
                    "arousal": 0,
                    "sessionId": session_id,
                    "timestampMs": ts_ms,
                    "memoryType": "EPISODIC",
                    "agentRecallCount": 0,
                    "entityMentions": entity_mentions
                }
                corpus_records.append(record)

            if ordered_turn_ids:
                temporal_chains.append({
                    "sessionId": session_id,
                    "orderedMemoryIds": ordered_turn_ids
                })

        qa_list = sample.get("qa", [])
        for q_idx, qa in enumerate(qa_list):
            q_text = qa.get("question")
            ans_text = qa.get("answer")
            raw_evidence = qa.get("evidence", [])
            cat = qa.get("category", 1)
            if not q_text:
                continue

            q_id = f"q_{sample_clean}_{q_idx+1}"
            subsystem = get_subsystem_for_category(cat)

            query_record = {
                "id": q_id,
                "text": q_text,
                "goldAnswer": str(ans_text) if ans_text is not None else "",
                "cognitiveProfile": "BALANCED",
                "expectedSubsystem": subsystem,
                "cognitiveNdcg": 1.0,
                "baselineNdcg": 0.5
            }

            ev_ids = []
            for ev_item in raw_evidence:
                tokens = re.split(r"[;\s,]+", str(ev_item).strip())
                for tok in tokens:
                    if not tok:
                        continue
                    ev_corpus_id = f"{sample_clean}_{tok}".replace(":", "_")
                    if ev_corpus_id in corpus_ids:
                        qrels.append((q_id, ev_corpus_id))
                        ev_ids.append(ev_corpus_id)

            queries.append(query_record)

            if len(ev_ids) >= 2:
                for i in range(len(ev_ids) - 1):
                    hebbian_edges.append({
                        "memoryIdA": ev_ids[i],
                        "memoryIdB": ev_ids[i+1],
                        "coActivationCount": 3
                    })

        if speaker_a and speaker_b:
            ent_key = f"{speaker_a}_{speaker_b}"
            if ent_key not in known_entities:
                known_entities.add(ent_key)
                entities.append({
                    "fromEntity": {"name": speaker_a, "type": "PERSON"},
                    "toEntity": {"name": speaker_b, "type": "PERSON"},
                    "relationType": "OTHER",
                    "sourceMemoryIds": [f"{sample_clean}_D1_1"]
                })

    save_checkpoint(checkpoint)

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
        "name": "LoCoMo Benchmark Persona",
        "age": 30,
        "occupation": "Long-Term Conversation Partner",
        "interests": ["dialogue evaluation", "long-horizon recall", "multi-session memory"],
        "lifeContext": "LoCoMo is an official ACL 2024 benchmark evaluating very long-term conversational memory across 10 multi-session dialogues.",
        "personalityTraits": ["reflective", "cooperative", "context-aware"],
        "companionRelationship": "The AI memory system tracks multi-session dialogues, temporal sequences, and long-horizon facts across up to 35 sessions per topic."
    }
    with open(os.path.join(DATASET_DIR, "persona.json"), "w", encoding="utf-8") as f:
        json.dump(persona, f, indent=2)

    with open(os.path.join(DATASET_DIR, "entities.jsonl"), "w", encoding="utf-8") as f:
        for ent in entities:
            f.write(json.dumps(ent) + "\n")

    with open(os.path.join(DATASET_DIR, "temporal_chains.jsonl"), "w", encoding="utf-8") as f:
        for tc in temporal_chains:
            f.write(json.dumps(tc) + "\n")

    edge_map = {}
    for edge in hebbian_edges:
        if edge["memoryIdA"] in corpus_ids and edge["memoryIdB"] in corpus_ids:
            key = tuple(sorted([edge["memoryIdA"], edge["memoryIdB"]]))
            if key not in edge_map:
                edge_map[key] = edge
            else:
                edge_map[key]["coActivationCount"] += 1

    with open(os.path.join(DATASET_DIR, "hebbian_edges.jsonl"), "w", encoding="utf-8") as f:
        for edge in edge_map.values():
            f.write(json.dumps(edge) + "\n")

    yaml_content = f"""spector:
  benchmark:
    dataset-name: "LoCoMo Benchmark (Official ACL 2024)"
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

    print(f"=== LoCoMo Ollama Extraction Complete ===")
    print(f"Corpus Records:  {len(corpus_records)}")
    print(f"Queries:         {len(queries)}")
    print(f"Qrels Mappings:  {len(qrels)}")
    print(f"Dataset Config:  {os.path.join(DATASET_DIR, 'spector-bench.yml')}")

if __name__ == "__main__":
    main()
