#!/usr/bin/env python3
"""
Spector Memory — Cognitive Dataset Enrichment Pipeline for LoCoMo.

Extracts rich cognitive metadata (Entities, Typed Hypergraph Relations,
Neuromodulatory Valence/Arousal, ICNU IngestionHints, and Synaptic Tags)
using Google Gemini API (gemini-3.1-flash-lite / gemini-2.0-flash-lite) or local Ollama.

Features:
- High-throughput batching (5 turns per batch) to optimize latency and token efficiency.
- Google Gemini REST API integration (zero pip dependencies, uses standard urllib).
- Local Ollama fallback support (qwen3.5:latest / llama3.1:latest).
- Resumable checkpointing (locomo_enrichment_checkpoint.jsonl).
- Automatic generation of updated corpus.jsonl and entities.jsonl.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional

# Ensure UTF-8 output on Windows consoles
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

EXTRACTION_SYSTEM_PROMPT = """You are a cognitive neuroscience & knowledge graph extraction engine for the Spector Cognitive Memory Architecture.

Extract entities, typed hypergraph relations, neuromodulatory psychological cues (valence, arousal, ICNU), and thematic synaptic tags for each of the provided dialogue turns.

Extraction Rules:
1. entityMentions: List all distinct entities mentioned in the turn with their types:
   Types: PERSON, ORGANIZATION, EVENT, LOCATION, CONCEPT, OBJECT, HOBBY, WORK, PREFERENCE, FAMILY, ACTIVITY
2. relations: List all directed factual relations linking the speaker or entities:
   fromEntity: {"name": "...", "type": "..."}
   toEntity: {"name": "...", "type": "..."}
   relationType: ATTENDED | PURCHASED | CREATED | EXPERIENCED | DISCUSSED | FEELS_ABOUT | PARTICIPATED_IN | RELATED_TO | EMPLOYED_BY | PARENT_OF | HAS_GOAL | RESEARCHED | SOLVED
   sourceMemoryId: the exact turn ID
3. valence: Integer between -128 (extreme grief/anger/frustration) and +127 (extreme joy/gratitude/satisfaction). 0 is neutral.
4. arousal: Integer between 0 (calm/lethargic) and 255 (high energy/excitement/shock/panic).
5. interest: Float between 0.0 and 1.0 (novelty/curiosity/focus).
6. challenge: Float between 0.0 and 1.0 (difficulty/effort/stress).
7. urgency: Float between 0.0 and 1.0 (time sensitivity/deadline).
8. synapticTags: Array of 2-5 concise semantic topic tags (e.g. ["lgbtq_support", "social_connection", "caroline"]).

Respond strictly in valid JSON format:
{
  "turns": [
    {
      "id": "turn_id_string",
      "entityMentions": [{"name": "Caroline", "type": "PERSON"}],
      "relations": [
        {"fromEntity": {"name": "Caroline", "type": "PERSON"}, "toEntity": {"name": "LGBTQ Support Group", "type": "ORGANIZATION"}, "relationType": "ATTENDED", "sourceMemoryId": "turn_id_string"}
      ],
      "valence": 45,
      "arousal": 120,
      "interest": 0.8,
      "challenge": 0.2,
      "urgency": 0.1,
      "synapticTags": ["lgbtq_support", "community", "caroline"]
    }
  ]
}"""

def clean_json_text(text: str) -> str:
    """Extract clean JSON substring from model response."""
    if not text:
        return "{}"
    # Strip markdown fences
    text = re.sub(r'^```json\s*', '', text.strip(), flags=re.MULTILINE)
    text = re.sub(r'^```\s*', '', text.strip(), flags=re.MULTILINE)
    text = text.strip()
    
    # Locate first { and last }
    first_brace = text.find('{')
    last_brace = text.rfind('}')
    if first_brace != -1 and last_brace != -1 and last_brace > first_brace:
        return text[first_brace:last_brace + 1]
    return text

def query_gemini_api(
    prompt: str,
    api_key: str,
    model: str = "gemini-3.1-flash-lite",
    max_retries: int = 4
) -> str:
    """Call Google Gemini REST API with retry and rate-limit backoff."""
    # Support model aliases (e.g. gemini-3.1-flash-lite -> gemini-2.0-flash-lite if 3.1 is not on public endpoint)
    target_model = model
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:generateContent?key={api_key}"
    
    payload = {
        "contents": [
            {
                "parts": [
                    {"text": EXTRACTION_SYSTEM_PROMPT + "\n\n" + prompt}
                ]
            }
        ],
        "generationConfig": {
            "temperature": 0.1,
            "responseMimeType": "application/json"
        }
    }
    data_bytes = json.dumps(payload).encode("utf-8")

    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(
                url,
                data=data_bytes,
                headers={"Content-Type": "application/json"}
            )
            with urllib.request.urlopen(req, timeout=60) as resp:
                result = json.loads(resp.read().decode("utf-8"))
                candidates = result.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        return parts[0].get("text", "").strip()
                return "{}"
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="ignore")
            # If 404 on custom model name, fallback to gemini-2.0-flash or gemini-1.5-flash
            if e.code == 404 and target_model != "gemini-2.0-flash" and target_model != "gemini-1.5-flash":
                print(f"Warning: Model {target_model} returned 404. Falling back to gemini-2.0-flash...", file=sys.stderr)
                target_model = "gemini-2.0-flash"
                url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:generateContent?key={api_key}"
                continue
            if e.code in (429, 503):
                wait_time = (attempt + 1) * 3
                print(f"Rate limited (HTTP {e.code}). Retrying in {wait_time}s...", file=sys.stderr)
                time.sleep(wait_time)
                continue
            raise RuntimeError(f"Gemini API HTTP {e.code}: {err_body}")
        except Exception as e:
            if attempt < max_retries - 1:
                time.sleep((attempt + 1) * 2)
                continue
            raise RuntimeError(f"Gemini API request failed: {e}")

    return "{}"

def query_ollama_api(
    prompt: str,
    url: str = "http://127.0.0.1:11434",
    model: str = "qwen3.5:latest"
) -> str:
    """Call Local Ollama API."""
    full_prompt = EXTRACTION_SYSTEM_PROMPT + "\n\n" + prompt
    payload = {
        "model": model,
        "prompt": full_prompt,
        "format": "json",
        "stream": False,
        "options": {
            "temperature": 0.1
        }
    }
    req = urllib.request.Request(
        f"{url.rstrip('/')}/api/generate",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        return data.get("response", "").strip()

def load_checkpoint(checkpoint_file: str) -> Dict[str, Dict[str, Any]]:
    """Load previously enriched records from checkpoint JSONL."""
    enriched = {}
    if os.path.exists(checkpoint_file):
        with open(checkpoint_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        rec = json.loads(line)
                        rid = rec.get("id")
                        if rid:
                            enriched[rid] = rec
                    except Exception:
                        pass
    return enriched

def main():
    parser = argparse.ArgumentParser(description="Enrich LoCoMo Benchmark Dataset with Full Cognitive Metadata")
    parser.add_argument("--dataset-dir", type=str, default=r"D:\git\spector-datasets\locomo\data", help="Path to locomo data directory")
    parser.add_argument("--gemini-api-key", type=str, default="", help="Google Gemini API Key (or set GEMINI_API_KEY env)")
    parser.add_argument("--gemini-model", type=str, default="gemini-3.1-flash-lite", help="Gemini model name")
    parser.add_argument("--ollama-model", type=str, default="", help="Use local Ollama model if set (e.g. qwen3.5:latest)")
    parser.add_argument("--ollama-url", type=str, default="http://127.0.0.1:11434", help="Ollama endpoint URL")
    parser.add_argument("--batch-size", type=int, default=5, help="Number of turns per extraction prompt (default: 5)")
    parser.add_argument("--limit", type=int, default=0, help="Limit number of turns to enrich (0 = all)")
    parser.add_argument("--delay-ms", type=int, default=100, help="Delay between API calls in ms")
    parser.add_argument("--fresh", action="store_true", default=False, help="Overwrite existing checkpoint")

    args = parser.parse_args()

    api_key = args.gemini_api_key or os.environ.get("GEMINI_API_KEY", "") or os.environ.get("GOOGLE_API_KEY", "")
    use_gemini = bool(api_key) and not bool(args.ollama_model)

    data_dir = os.path.abspath(args.dataset_dir)
    corpus_file = os.path.join(data_dir, "corpus.jsonl")
    entities_file = os.path.join(data_dir, "entities.jsonl")
    checkpoint_file = os.path.join(data_dir, "locomo_enrichment_checkpoint.jsonl")

    if not os.path.exists(corpus_file):
        print(f"Error: corpus.jsonl not found at {corpus_file}", file=sys.stderr)
        sys.exit(1)

    print("=" * 70)
    print(" Spector Memory — Cognitive Dataset Enrichment Pipeline")
    print(f" Dataset Dir:  {data_dir}")
    print(f" Engine:       {'Gemini API (' + args.gemini_model + ')' if use_gemini else 'Local Ollama (' + (args.ollama_model or 'qwen3.5:latest') + ')'}")
    print(f" Batch Size:   {args.batch_size} turns/call | Pacing Delay: {args.delay_ms} ms")
    print("=" * 70)

    # Load original corpus records
    original_records = []
    with open(corpus_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                original_records.append(json.loads(line))

    if args.limit > 0:
        original_records = original_records[:args.limit]
        print(f"Limiting enrichment to first {len(original_records)} records.")

    completed_map = load_checkpoint(checkpoint_file) if not args.fresh else {}
    if completed_map:
        print(f"Resuming: {len(completed_map)}/{len(original_records)} turns already enriched in checkpoint.")

    checkpoint_writer = open(checkpoint_file, "w" if args.fresh else "a", encoding="utf-8", buffering=1)

    all_enriched_records = []
    all_extracted_relations = []
    seen_relation_keys = set()

    # If resuming, load existing relations from checkpoint
    for rec in completed_map.values():
        all_enriched_records.append(rec)
        for rel in rec.get("_extracted_relations", []):
            rel_key = (
                rel.get("fromEntity", {}).get("name", ""),
                rel.get("toEntity", {}).get("name", ""),
                rel.get("relationType", ""),
                rel.get("sourceMemoryIds", [""])[0] if rel.get("sourceMemoryIds") else ""
            )
            if rel_key not in seen_relation_keys:
                seen_relation_keys.add(rel_key)
                all_extracted_relations.append(rel)

    # Group into batches
    batches = []
    current_batch = []
    for rec in original_records:
        if rec["id"] in completed_map:
            continue
        current_batch.append(rec)
        if len(current_batch) >= args.batch_size:
            batches.append(current_batch)
            current_batch = []
    if current_batch:
        batches.append(current_batch)

    print(f"Processing {len(batches)} batches ({sum(len(b) for b in batches)} turns to extract)...")

    processed_count = len(completed_map)
    total_turns = len(original_records)

    try:
        for b_idx, batch in enumerate(batches):
            # Format prompt for batch
            prompt_lines = ["Extract cognitive metadata for the following conversational dialogue turns:"]
            for turn in batch:
                tid = turn.get("id", "")
                text = turn.get("text", "")
                speaker = turn.get("speaker", text.split(":")[0] if ":" in text else "User")
                prompt_lines.append(f"\n[Turn ID: {tid}]\n{text}")

            prompt = "\n".join(prompt_lines)

            # Call extraction model
            t0 = time.perf_counter()
            raw_response = "{}"
            try:
                if use_gemini:
                    raw_response = query_gemini_api(prompt, api_key, model=args.gemini_model)
                else:
                    ollama_m = args.ollama_model or "qwen3.5:latest"
                    raw_response = query_ollama_api(prompt, url=args.ollama_url, model=ollama_m)
            except Exception as e:
                print(f"Extraction error on batch {b_idx+1}: {e}", file=sys.stderr)

            elapsed_s = time.perf_counter() - t0

            # Parse JSON
            cleaned_json = clean_json_text(raw_response)
            extracted_turns_map = {}
            try:
                parsed = json.loads(cleaned_json)
                turns_arr = parsed.get("turns", []) if isinstance(parsed, dict) else []
                for t_obj in turns_arr:
                    tid = t_obj.get("id")
                    if tid:
                        extracted_turns_map[tid] = t_obj
            except Exception as e:
                print(f"Warning: Failed to parse batch JSON on batch {b_idx+1}: {e}", file=sys.stderr)

            # Update records
            for orig in batch:
                tid = orig["id"]
                ext = extracted_turns_map.get(tid, {})

                # Merge entity mentions
                entity_mentions = ext.get("entityMentions", orig.get("entityMentions", []))
                
                # Merge relations
                raw_relations = ext.get("relations", [])
                turn_relations = []
                for rel in raw_relations:
                    from_ent = rel.get("fromEntity", {})
                    to_ent = rel.get("toEntity", {})
                    rel_type = rel.get("relationType", "RELATED_TO").upper()
                    if from_ent.get("name") and to_ent.get("name"):
                        rel_obj = {
                            "fromEntity": from_ent,
                            "toEntity": to_ent,
                            "relationType": rel_type,
                            "sourceMemoryIds": [tid]
                        }
                        turn_relations.append(rel_obj)
                        rel_key = (from_ent.get("name"), to_ent.get("name"), rel_type, tid)
                        if rel_key not in seen_relation_keys:
                            seen_relation_keys.add(rel_key)
                            all_extracted_relations.append(rel_obj)

                # Psychological cues
                valence = int(ext.get("valence", orig.get("valence", 0)))
                valence = max(-128, min(127, valence))
                
                arousal = int(ext.get("arousal", orig.get("arousal", 0)))
                arousal = max(0, min(255, arousal))

                interest = float(ext.get("interest", 0.5))
                challenge = float(ext.get("challenge", 0.1))
                urgency = float(ext.get("urgency", 0.1))

                # Synaptic tags
                ext_tags = ext.get("synapticTags", [])
                combined_tags = list(dict.fromkeys(orig.get("synapticTags", []) + ext_tags))

                enriched_rec = {
                    "id": tid,
                    "text": orig.get("text", ""),
                    "title": orig.get("title", ""),
                    "synapticTags": combined_tags,
                    "valence": valence,
                    "arousal": arousal,
                    "importance": orig.get("importance", 1.0),
                    "interest": round(interest, 2),
                    "challenge": round(challenge, 2),
                    "urgency": round(urgency, 2),
                    "sessionId": orig.get("sessionId", ""),
                    "timestampMs": orig.get("timestampMs", 1700000000000),
                    "memoryType": orig.get("memoryType", "EPISODIC"),
                    "agentRecallCount": orig.get("agentRecallCount", 0),
                    "entityMentions": entity_mentions,
                    "_extracted_relations": turn_relations
                }

                all_enriched_records.append(enriched_rec)
                checkpoint_writer.write(json.dumps(enriched_rec) + "\n")
                processed_count += 1

            pct = (processed_count / total_turns) * 100.0
            print(f"[Batch {b_idx+1:4d}/{len(batches):4d}] Enriched: {processed_count:4d}/{total_turns:4d} ({pct:5.1f}%) | Time: {elapsed_s:4.2f}s | Relations: {len(all_extracted_relations):4d}")

            if args.delay_ms > 0:
                time.sleep(args.delay_ms / 1000.0)

    finally:
        checkpoint_writer.close()

    # Synchronize back to corpus.jsonl and entities.jsonl
    print("\nSynchronizing enriched dataset files...")
    
    # 1. Update full corpus map and write back
    full_corpus = []
    with open(corpus_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                full_corpus.append(json.loads(line))

    enriched_map = {r["id"]: {k: v for k, v in r.items() if not k.startswith("_")} for r in all_enriched_records}
    
    with open(corpus_file, "w", encoding="utf-8") as f:
        for orig_r in full_corpus:
            rec_to_write = enriched_map.get(orig_r["id"], orig_r)
            f.write(json.dumps(rec_to_write) + "\n")
    print(f"Updated: {corpus_file} ({len(full_corpus)} total records, {len(enriched_map)} enriched)")

    # 2. Merge existing entities.jsonl with all extracted relations
    existing_entities = []
    if os.path.exists(entities_file):
        with open(entities_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        existing_entities.append(json.loads(line))
                    except Exception:
                        pass

    for rel in existing_entities:
        rel_key = (
            rel.get("fromEntity", {}).get("name", ""),
            rel.get("toEntity", {}).get("name", ""),
            rel.get("relationType", ""),
            rel.get("sourceMemoryIds", [""])[0] if rel.get("sourceMemoryIds") else ""
        )
        if rel_key not in seen_relation_keys:
            seen_relation_keys.add(rel_key)
            all_extracted_relations.append(rel)

    with open(entities_file, "w", encoding="utf-8") as f:
        for rel in all_extracted_relations:
            f.write(json.dumps(rel) + "\n")
    print(f"Updated: {entities_file} ({len(all_extracted_relations)} total hypergraph relations)")

    print("\nCognitive Dataset Enrichment Finished Successfully!")

if __name__ == "__main__":
    main()
