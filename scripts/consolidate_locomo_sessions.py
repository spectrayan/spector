#!/usr/bin/env python3
"""
Spector Memory — Session Consolidation Pipeline for LoCoMo (Concurrent Version).

Implements the biological sleep consolidation concept:
  Episodic conversation turns → LLM fact distillation → Semantic facts

For each session, sends all turns to Gemini in a single call that extracts:
  - Distilled semantic facts (self-contained, third-person statements)
  - Entity mentions per fact
  - Typed relations per fact
  - Cognitive metadata (valence, arousal, ICNU, synaptic tags)

Output:
  - Updated corpus.jsonl with new SEMANTIC fact records appended
  - locomo_consolidation_checkpoint.jsonl for resumability
  - entities.jsonl updated with new relations
"""

import argparse
import datetime
import json
import os
import re
import sys
import time
import threading
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import defaultdict
from typing import Dict, Any, List, Optional

# Ensure UTF-8 output on Windows
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# ─────────────── Prompt Template ───────────────

CONSOLIDATION_SYSTEM_PROMPT = """You are an expert cognitive memory consolidation engine for an AI agent architecture.
Your job is to read a multi-turn conversation session between users and distill it into permanent, self-contained semantic facts with entity graphs and cognitive metadata."""

CONSOLIDATION_USER_PROMPT = """Consolidate the following conversation session into a list of distilled semantic facts.

Rules:
1. Each fact must be a complete, self-contained, third-person factual statement (e.g., "Melanie participated in a 5K charity run on July 10, 2023.").
2. Extract all personal preferences, activities, relationships, events, dates, and locations discussed.
3. If visual descriptions are present in the turns (e.g. [Visual: ...]), incorporate the visual facts into the semantic statements (e.g., specific objects shown, art styles, photo contents).
4. For each fact, identify named entities (People, Locations, Concepts, Organizations) and typed relationships between them.
5. Provide cognitive metadata:
   - valence: integer from -128 to 127 (negative = distressing, positive = joyous/supportive, 0 = neutral)
   - arousal: integer from 0 to 255 (0 = calm/routine, 255 = intense/exciting)
   - interest: float 0.0 to 1.0 (novelty/curiosity score)
   - challenge: float 0.0 to 1.0 (cognitive difficulty / problem-solving)
   - urgency: float 0.0 to 1.0 (time-sensitivity)
   - synapticTags: 2 to 5 relevant lowercase thematic tags (e.g. ["pottery", "art", "running", "family"])

Respond strictly in valid JSON format:
{
  "facts": [
    {
      "text": "Self-contained fact statement...",
      "valence": 20,
      "arousal": 45,
      "interest": 0.8,
      "challenge": 0.2,
      "urgency": 0.1,
      "synapticTags": ["topic1", "topic2"],
      "entityMentions": [
        {"name": "Entity Name", "type": "PERSON | LOCATION | CONCEPT | ORGANIZATION"}
      ],
      "relations": [
        {
          "fromEntity": {"name": "Entity A", "type": "PERSON"},
          "toEntity": {"name": "Entity B", "type": "CONCEPT"},
          "relationType": "HAS_PET | PARTICIPATED_IN | CREATED | PURCHASED | RECOMMENDED | SUPPORTS | RELATED_TO"
        }
      ]
    }
  ]
}"""

def clean_json_text(raw_text: str) -> str:
    cleaned = re.sub(r'```json\s*', '', raw_text)
    cleaned = re.sub(r'```\s*$', '', cleaned)
    cleaned = cleaned.strip()
    return cleaned

import ssl
ssl_ctx = ssl._create_unverified_context()

def query_gemini_api(prompt: str, api_key: str, model: str = "gemini-3.1-flash-lite", max_retries: int = 4) -> str:
    target_model = model.strip()
    if target_model.startswith("models/"):
        target_model = target_model[len("models/"):]

    url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:generateContent?key={api_key}"
    payload = {
        "contents": [
            {
                "role": "user",
                "parts": [{"text": prompt}]
            }
        ],
        "systemInstruction": {
            "parts": [{"text": CONSOLIDATION_SYSTEM_PROMPT + "\n\n" + CONSOLIDATION_USER_PROMPT}]
        },
        "generationConfig": {
            "temperature": 0.1,
            "responseMimeType": "application/json"
        }
    }

    req_data = json.dumps(payload).encode("utf-8")
    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(url, data=req_data, headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(req, context=ssl_ctx, timeout=45) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                candidates = data.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        return parts[0].get("text", "{}")
            return "{}"
        except urllib.error.HTTPError as e:
            if e.code in (429, 503) and attempt < max_retries - 1:
                time.sleep((2 ** attempt) * 1.5)
            else:
                raise
        except Exception:
            if attempt < max_retries - 1:
                time.sleep((2 ** attempt) * 1.0)
            else:
                raise
    return "{}"

def load_checkpoint(checkpoint_file: str) -> Dict[str, List[Dict[str, Any]]]:
    consolidated = {}
    if os.path.exists(checkpoint_file):
        with open(checkpoint_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        rec = json.loads(line)
                        sid = rec.get("sessionId", "")
                        if sid:
                            consolidated.setdefault(sid, []).append(rec)
                    except Exception:
                        pass
    return consolidated

def process_session(session_id: str, turns: List[Dict[str, Any]], api_key: str, model_name: str) -> List[Dict[str, Any]]:
    speakers = set()
    turn_lines = []
    for turn in turns:
        text = turn.get("text", "")
        if ":" in text:
            speaker = text.split(":")[0].strip()
            speakers.add(speaker)
        turn_lines.append(f"[{turn['id']}] {text}")

    session_ts = turns[0].get("timestampMs", 0) if turns else 0
    if session_ts > 0:
        instant = datetime.datetime.fromtimestamp(session_ts / 1000.0, tz=datetime.timezone.utc)
        session_date_str = instant.strftime("%d %B %Y")
    else:
        session_date_str = "Unknown Date"

    speaker_str = " and ".join(sorted(speakers)) if speakers else "the participants"
    prompt = f"Consolidate the following conversation session between {speaker_str} (Session Date: {session_date_str}, {len(turns)} turns, session: {session_id}):\n\n"
    prompt += "\n".join(turn_lines)

    try:
        raw_response = query_gemini_api(prompt, api_key, model=model_name)
    except Exception as e:
        print(f"  ERROR on {session_id}: {e}", file=sys.stderr)
        return []

    cleaned = clean_json_text(raw_response)
    facts_list = []
    try:
        parsed = json.loads(cleaned)
        facts_list = parsed.get("facts", []) if isinstance(parsed, dict) else []
    except Exception as e:
        print(f"  WARNING: Failed to parse JSON for {session_id}: {e}", file=sys.stderr)
        return []

    session_facts = []
    for f_idx, fact_data in enumerate(facts_list):
        fact_text = fact_data.get("text", "").strip()
        if not fact_text or len(fact_text) < 10:
            continue

        fact_id = f"fact_{session_id}_{f_idx + 1}"
        raw_tags = fact_data.get("synapticTags", [])
        clean_tags = []
        for tag in raw_tags:
            if isinstance(tag, str):
                t_clean = re.sub(r'[^a-zA-Z0-9_]', '_', tag.strip().lower()).strip('_')
                if len(t_clean) >= 3 and not t_clean.startswith("conv") and not t_clean.startswith("session") and not t_clean.startswith("locomo") and t_clean not in clean_tags:
                    clean_tags.append(t_clean)
        if not clean_tags:
            clean_tags = ["general_conversation"]

        structural_tags = []
        parts = session_id.split("_")
        if len(parts) >= 2:
            structural_tags.append(f"{parts[0]}_{parts[1]}")
        structural_tags.append(session_id.replace("_sess_", "_session_"))

        session_ts = turns[0].get("timestampMs", 1700000000000)
        valence = max(-128, min(127, int(fact_data.get("valence", 0))))
        arousal = max(0, min(255, int(fact_data.get("arousal", 0))))
        interest = round(max(0.0, min(1.0, float(fact_data.get("interest", 0.5)))), 2)
        challenge = round(max(0.0, min(1.0, float(fact_data.get("challenge", 0.0)))), 2)
        urgency = round(max(0.0, min(1.0, float(fact_data.get("urgency", 0.0)))), 2)

        entity_mentions = fact_data.get("entityMentions", [])
        valid_mentions = []
        for em in entity_mentions:
            if isinstance(em, dict) and em.get("name"):
                valid_mentions.append({"name": em["name"], "type": em.get("type", "CONCEPT")})

        relations = fact_data.get("relations", [])
        valid_relations = []
        for rel in relations:
            if isinstance(rel, dict):
                from_e = rel.get("fromEntity", {})
                to_e = rel.get("toEntity", {})
                if from_e.get("name") and to_e.get("name"):
                    rel_obj = {
                        "fromEntity": from_e,
                        "toEntity": to_e,
                        "relationType": rel.get("relationType", "RELATED_TO").upper(),
                        "sourceMemoryIds": [fact_id]
                    }
                    valid_relations.append(rel_obj)

        fact_record = {
            "id": fact_id,
            "text": fact_text,
            "title": f"Consolidated Fact from {session_id}",
            "synapticTags": structural_tags + clean_tags,
            "valence": valence,
            "arousal": arousal,
            "importance": 1.0,
            "interest": interest,
            "challenge": challenge,
            "urgency": urgency,
            "sessionId": session_id,
            "timestampMs": session_ts,
            "memoryType": "SEMANTIC",
            "agentRecallCount": 0,
            "entityMentions": valid_mentions,
            "_source_turn_ids": [t["id"] for t in turns],
            "_extracted_relations": valid_relations
        }
        session_facts.append(fact_record)

    return session_facts

def main():
    parser = argparse.ArgumentParser(description="Spector Session Consolidation — Concurrent Fact Distillation")
    parser.add_argument("--dataset-dir", type=str, default=r"D:\git\spector-datasets\locomo\data")
    parser.add_argument("--gemini-api-key", type=str, default="")
    parser.add_argument("--gemini-model", type=str, default="gemini-3.1-flash-lite")
    parser.add_argument("--concurrency", type=int, default=8, help="Number of concurrent worker threads (default: 8)")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--fresh", action="store_true", default=False)
    parser.add_argument("--facts-file", type=str, default="")
    parser.add_argument("--rebuild-only", action="store_true", default=False)
    parser.add_argument("--conv-filter", type=str, default="")
    args = parser.parse_args()

    data_dir = os.path.abspath(args.dataset_dir)
    corpus_file = os.path.join(data_dir, "corpus.jsonl")
    checkpoint_file = os.path.join(data_dir, "locomo_consolidation_checkpoint.jsonl")
    facts_file = os.path.abspath(args.facts_file) if args.facts_file else os.path.join(data_dir, "locomo_consolidated_facts.jsonl")
    entities_file = os.path.join(data_dir, "entities.jsonl")

    api_key = ""
    if not args.rebuild_only:
        api_key = args.gemini_api_key or os.environ.get("GEMINI_API_KEY", "") or os.environ.get("GOOGLE_API_KEY", "")
        if not api_key:
            print("Error: No Gemini API key provided.", file=sys.stderr)
            sys.exit(1)

    print("=" * 70)
    print(f" Spector Memory — Concurrent Session Consolidation (Workers: {args.concurrency})")
    print(f" Dataset Dir:  {data_dir}")
    print(f" Facts Store:  {facts_file}")
    print("=" * 70)

    corpus_records = []
    with open(corpus_file, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                corpus_records.append(json.loads(line.strip()))

    episodic_records = [r for r in corpus_records if not r.get("id", "").startswith("fact_")]
    sessions = defaultdict(list)
    for rec in episodic_records:
        sid = rec.get("sessionId", "unknown")
        sessions[sid].append(rec)

    for sid in sessions:
        sessions[sid].sort(key=lambda r: r.get("id", ""))

    if args.conv_filter:
        sessions = {sid: turns for sid, turns in sessions.items() if args.conv_filter in sid}

    print(f"Loaded {len(episodic_records)} episodic turns across {len(sessions)} sessions")

    completed_sessions = {}
    if not args.fresh:
        if os.path.exists(facts_file):
            completed_sessions = load_checkpoint(facts_file)
        elif os.path.exists(checkpoint_file):
            completed_sessions = load_checkpoint(checkpoint_file)

    all_new_facts = []
    all_new_relations = []

    if args.rebuild_only:
        for sid, facts in completed_sessions.items():
            all_new_facts.extend(facts)
            for fact in facts:
                for rel in fact.get("_extracted_relations", []):
                    all_new_relations.append(rel)
    else:
        session_ids = sorted(sessions.keys())
        if args.limit > 0:
            session_ids = session_ids[:args.limit]

        pending = [sid for sid in session_ids if sid not in completed_sessions]
        print(f"Processing {len(pending)} pending sessions with {args.concurrency} worker threads...\n")

        for sid, facts in completed_sessions.items():
            all_new_facts.extend(facts)
            for fact in facts:
                for rel in fact.get("_extracted_relations", []):
                    all_new_relations.append(rel)

        checkpoint_writer = open(checkpoint_file, "w" if args.fresh else "a", encoding="utf-8", buffering=1)
        facts_writer = open(facts_file, "w" if args.fresh else "a", encoding="utf-8", buffering=1)
        write_lock = threading.Lock()
        processed_count = len(completed_sessions)
        total_sessions = len(session_ids)

        t_start = time.perf_counter()
        with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
            future_to_sid = {
                executor.submit(process_session, sid, sessions[sid], api_key, args.gemini_model): sid
                for sid in pending
            }

            for future in as_completed(future_to_sid):
                sid = future_to_sid[future]
                try:
                    s_facts = future.result()
                    with write_lock:
                        for fact in s_facts:
                            line_str = json.dumps(fact, ensure_ascii=False) + "\n"
                            checkpoint_writer.write(line_str)
                            facts_writer.write(line_str)
                            all_new_facts.append(fact)
                            for rel in fact.get("_extracted_relations", []):
                                all_new_relations.append(rel)

                        processed_count += 1
                        pct = (processed_count / total_sessions) * 100.0
                        elapsed = time.perf_counter() - t_start
                        rate = (processed_count - len(completed_sessions)) / max(0.1, elapsed)
                        print(f"[{processed_count:3d}/{total_sessions:3d}] {sid:20s} -> {len(s_facts):2d} facts | {rate:.1f} sess/s | {pct:.1f}%")
                except Exception as e:
                    print(f"Error on {sid}: {e}", file=sys.stderr)

        checkpoint_writer.close()
        facts_writer.close()

    print(f"\nConsolidation complete: {len(all_new_facts)} total semantic facts")

    clean_episodic = []
    for r in corpus_records:
        if r.get("id", "").startswith("fact_"):
            continue
        clean_r = dict(r)
        clean_r["memoryType"] = "EPISODIC"
        struct_tags = [t for t in r.get("synapticTags", []) if re.match(r'^conv_\w+', t)]
        clean_r["synapticTags"] = struct_tags
        clean_r.pop("entityMentions", None)
        clean_episodic.append(clean_r)

    final_corpus = clean_episodic + [{k: v for k, v in fact.items() if not k.startswith("_")} for fact in all_new_facts]

    with open(corpus_file, "w", encoding="utf-8") as f:
        for rec in final_corpus:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

    print(f"Saved: {corpus_file} (Total: {len(final_corpus)} records)")

    # Ingest entities
    if all_new_relations:
        with open(entities_file, "w", encoding="utf-8") as f:
            for rel in all_new_relations:
                f.write(json.dumps(rel, ensure_ascii=False) + "\n")
        print(f"Saved: {entities_file} ({len(all_new_relations)} relations)")

    # Clear memory indices
    for stale in ["embeddings.bin", "ingested-memory"]:
        sp = os.path.join(data_dir, stale)
        if os.path.exists(sp):
            if os.path.isdir(sp):
                import shutil
                shutil.rmtree(sp)
            else:
                os.remove(sp)

if __name__ == "__main__":
    main()
