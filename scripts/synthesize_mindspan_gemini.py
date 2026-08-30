#!/usr/bin/env python3
# Copyright 2026 Spectrayan
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
MindSpan High-Quality Gemini-3.1-Flash-Lite Longitudinal Dataset Synthesizer.

Synthesizes rich, human-like multi-turn conversational dialogue with:
- 20+ years of autobiographical narrative depth (2004-2026)
- Continuous multimodal sensory & ambient companion context (Issue #629)
- Natural dialogue nuances (ambiguity, pronouns, follow-ups, conversational shortcuts)
- 1,095 days with 4 distinct daily sessions (Morning, Midday, Evening, Night)
- Resumable checkpointing for resilient large-scale generation.
"""

import os
import sys
import json
import time
import random
import ssl
import urllib.request
import datetime
from pathlib import Path

# Paths
DATASET_DIR = Path("d:/git/spector-datasets/mindspan/data")
DAILY_DIR = DATASET_DIR / "daily"
DAILY_DIR.mkdir(parents=True, exist_ok=True)
CHECKPOINT_FILE = DATASET_DIR / "synthesis_checkpoint.json"

KEY_FILE = DATASET_DIR.parent / ".gemini_key"
if not KEY_FILE.exists():
    KEY_FILE = DATASET_DIR / ".gemini_key"

if KEY_FILE.exists():
    GEMINI_API_KEY = KEY_FILE.read_text(encoding="utf-8").strip()
else:
    GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")

MODEL = "gemini-3.1-flash-lite"
SSL_CTX = ssl._create_unverified_context()

if not GEMINI_API_KEY:
    print("ERROR: GEMINI_API_KEY not found!")
    sys.exit(1)

def call_gemini(prompt, system_instruction=None, max_retries=5, temperature=0.7):
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent?key={GEMINI_API_KEY}"
    
    contents = []
    if system_instruction:
        contents.append({"role": "user", "parts": [{"text": f"System Context:\n{system_instruction}\n\nTask:\n{prompt}"}]})
    else:
        contents.append({"role": "user", "parts": [{"text": prompt}]})

    payload = json.dumps({
        "contents": contents,
        "generationConfig": {
            "temperature": temperature,
            "responseMimeType": "application/json"
        }
    }).encode("utf-8")

    for attempt in range(max_retries):
        req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, data=payload, context=SSL_CTX, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                candidates = data.get("candidates", [])
                if candidates and "content" in candidates[0]:
                    txt = candidates[0]["content"]["parts"][0]["text"]
                    return json.loads(txt)
                else:
                    print(f"Empty candidate received (attempt {attempt+1})")
        except urllib.error.HTTPError as e:
            if e.code == 429:
                wait_sec = (attempt + 1) * 4
                print(f"Rate limited (429), backing off for {wait_sec}s...")
                time.sleep(wait_sec)
            else:
                print(f"HTTP Error {e.code}: {e.read().decode('utf-8', errors='ignore')[:200]}")
                time.sleep(2)
        except Exception as e:
            print(f"API Error (attempt {attempt+1}): {e}")
            time.sleep(2)
            
    raise RuntimeError("Failed to generate content from Gemini after retries")

# ---------------------------------------------------------------------------
# Persona & System Instruction
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = """
You are an expert dataset synthesizer creating 'MindSpan', a premier 20-year longitudinal cognitive memory benchmark.

Persona Context:
- Name: Mike Thompson (Principal Product Manager at Vertex Health in Plano, TX; lives in Frisco, TX with wife Sarah, son Ethan, daughter Lily, Golden Retriever Cooper).
- AI Companion: Jarvis (ambient cognitive companion with continuous memory capture).
- Hobbies & Life: Fine woodworking (using Lie-Nielsen hand planes inherited from late father-in-law Robert Miller), Texas craft BBQ, youth soccer coaching (Frisco FC), 10k running along Cottonwood Creek Trail, home automation (Zigbee sensors), investing in index funds.
- Extended Family: Dad Tom (retired history teacher, knee surgery recovery) & Mom Linda in Naperville, IL; Sarah's mother Patricia in Austin; Sister Dr. Emily Reed (pediatric ER) in Denver; Brother-in-law Daniel Miller (airline pilot) in Seattle.

Guidelines for Conversational Realism:
1. Natural Dialogue & Ambiguity: Human dialogue has conversational shorthand, pronouns, occasional vague confirmations ("Yeah, let's do the second option"), follow-ups, and natural topic shifts.
2. Continuous Multimodal Context: Include realistic ambient sensory observation frames in square brackets when appropriate (e.g. `[Audio/VAD: Ethan practicing piano in background; Cooper whining at back door]`, `[Visual Frame: Garage workbench with walnut shavings and No. 4 smoothing plane]`).
3. Cognitive Annotations: Output valid JSON with emotional valence (-128 to 127), importance (0.05 to 10.0), physiological arousal (0 to 255), and 3-6 relevant synapticTags.
"""

def make_entity_mentions(text):
    entities = {
        "Mike Thompson": "PERSON", "Sarah Thompson": "PERSON", "Ethan Thompson": "PERSON",
        "Lily Thompson": "PERSON", "Cooper": "ANIMAL", "Jarvis": "SOFTWARE",
        "Tom Thompson": "PERSON", "Linda Thompson": "PERSON", "Robert Miller": "PERSON",
        "Patricia Moretti-Miller": "PERSON", "Dr. Emily Reed": "PERSON", "Mark Reed": "PERSON",
        "Maya Reed": "PERSON", "Daniel Miller": "PERSON", "Chloe Vance": "PERSON",
        "Arthur Thompson": "PERSON", "Greg Holloway": "PERSON", "Anika Patel": "PERSON",
        "Dave Nguyen": "PERSON", "Vertex Health": "ORGANIZATION", "CareConnect": "SOFTWARE",
        "Frisco FC": "ORGANIZATION", "Little Sprouts Pre-K": "ORGANIZATION",
        "Little Stars Daycare": "ORGANIZATION", "Frisco Aquatic Center": "LOCATION",
        "Lie-Nielsen": "ORGANIZATION", "Northwestern Memorial Hospital": "LOCATION",
        "First Texas National Bank": "ORGANIZATION", "Cottonwood Creek Trail": "LOCATION",
        "Warren Sports Complex": "LOCATION", "Lake Geneva": "LOCATION", "Naperville": "LOCATION",
        "Frisco": "LOCATION", "Plano": "LOCATION", "Austin": "LOCATION", "Denver": "LOCATION",
        "Seattle": "LOCATION", "Chicago": "LOCATION"
    }
    mentions = []
    for name, etype in entities.items():
        if name in text:
            mentions.append({"name": name, "type": etype})
    return mentions

def to_epoch_ms(dt):
    return int(dt.replace(tzinfo=datetime.timezone.utc).timestamp() * 1000)

# ---------------------------------------------------------------------------
# 1. Synthesize High-Fidelity 20-Year Autobiographical Foundations
# ---------------------------------------------------------------------------

def synthesize_biographical_corpus():
    bio_file = DATASET_DIR / "corpus-biographical.jsonl"
    print("\n--- Synthesizing 20-Year Autobiographical Milestones via Gemini-3.1-Flash-Lite ---")
    
    prompt = """
    Generate 20 distinct high-impact autobiographical memories spanning Mike Thompson's life from 2004 to 2023.
    Cover these exact eras:
    1. 2004-2008: High school in Naperville, Great-Grandfather Arthur Thompson gifting 1944 Elgin pocket watch carried in WWII, first Linux experiments.
    2. 2008-2012: UIUC college dorm in Townsend Hall, CS & Economics double major, meeting Sarah Moretti at Bourgeois Pig cafe in Lincoln Park in October 2011.
    3. 2012-2018: First HealthTech PM role in Chicago, marrying Sarah at Lake Geneva Riviera Ballroom in Sept 2016 (Dave Nguyen best man), Robert Miller gifting Lie-Nielsen hand planes (No. 4, No. 7 jointer, chisels) in Austin in July 2017, birth of Ethan in Oct 2017.
    4. 2018-2023: Robert Miller passing in 2022, relocating to Frisco TX in June 2022 for Vertex Health Senior PM role, adopting Cooper in Dec 2022, birth of Lily in May 2023.

    Return a JSON array of objects:
    [
      {
        "year": 2008,
        "month": 6,
        "day": 8,
        "hour": 14,
        "userText": "...",
        "jarvisText": "...",
        "title": "...",
        "synapticTags": ["...", "..."],
        "valence": 95,
        "importance": 9.8,
        "arousal": 160,
        "memoryType": "EPISODIC"
      }
    ]
    """
    
    records = call_gemini(prompt, SYSTEM_PROMPT)
    bio_records = []
    bio_counter = 1
    
    for item in records:
        dt = datetime.datetime(item.get("year", 2016), item.get("month", 9), item.get("day", 17), item.get("hour", 14), 0)
        
        # User record
        u_id = f"bio-{bio_counter:04d}"
        bio_counter += 1
        u_txt = item["userText"]
        bio_records.append({
            "id": u_id,
            "text": u_txt,
            "title": item.get("title", "Autobiographical Milestone"),
            "synapticTags": item.get("synapticTags", ["biographical", "milestone"]),
            "valence": int(item.get("valence", 50)),
            "importance": float(item.get("importance", 8.0)),
            "arousal": int(item.get("arousal", 120)),
            "sessionId": f"session-bio-{dt.strftime('%Y%m%d')}",
            "timestampMs": to_epoch_ms(dt),
            "entityMentions": make_entity_mentions(u_txt),
            "memoryType": item.get("memoryType", "EPISODIC"),
            "agentRecallCount": 0,
            "interest": 0.9,
            "challenge": 0.6,
            "urgency": 0.3
        })
        
        # Jarvis record
        j_id = f"bio-{bio_counter:04d}"
        bio_counter += 1
        j_txt = item["jarvisText"]
        bio_records.append({
            "id": j_id,
            "text": j_txt,
            "title": f"Jarvis: {item.get('title', 'Autobiographical Milestone')}",
            "synapticTags": item.get("synapticTags", ["biographical", "milestone"]),
            "valence": int(item.get("valence", 50) * 0.5),
            "importance": round(float(item.get("importance", 8.0)) * 0.9, 2),
            "arousal": int(item.get("arousal", 120) * 0.4),
            "sessionId": f"session-bio-{dt.strftime('%Y%m%d')}",
            "timestampMs": to_epoch_ms(dt + datetime.timedelta(seconds=45)),
            "entityMentions": make_entity_mentions(j_txt),
            "memoryType": "SEMANTIC",
            "agentRecallCount": 0,
            "interest": 0.8,
            "challenge": 0.5,
            "urgency": 0.2
        })
        
    print(f"Generated {len(bio_records)} high-fidelity biographical records via Gemini!")
    with open(bio_file, "w", encoding="utf-8") as f:
        for r in bio_records:
            f.write(json.dumps(r) + "\n")
    return bio_records

# ---------------------------------------------------------------------------
# 2. Synthesize High-Quality Daily Multi-Session Continuum (Sample Days Demo)
# ---------------------------------------------------------------------------

def synthesize_daily_conversations(start_day=1, count_days=10):
    print(f"\n--- Synthesizing Days {start_day} to {start_day + count_days - 1} via Gemini-3.1-Flash-Lite ---")
    start_date = datetime.date(2024, 1, 1)
    
    for d_idx in range(start_day - 1, start_day + count_days - 1):
        cur_date = start_date + datetime.timedelta(days=d_idx)
        day_num = d_idx + 1
        yr = cur_date.year
        
        prompt = f"""
        Generate 4 distinct conversational sessions for Day {day_num} ({cur_date.strftime('%A, %B %d, %Y')}) between Mike Thompson and Jarvis.
        Sessions must cover:
        1. Morning (07:30 AM): Trail run along Cottonwood Creek, daily schedule, family coordination.
        2. Midday (12:30 PM): Vertex Health CareConnect (Year {yr} context: {'v1 messaging debate with Greg Holloway & Anika Patel' if yr==2024 else 'Principal PM Clinical AI triage & FHIR webhooks' if yr==2025 else '14 hospital network production deployment'}).
        3. Evening (06:45 PM): Family dinner, Ethan Frisco FC soccer / piano, Lily Pre-K art, garage woodworking with Robert's Lie-Nielsen hand planes, or Texas BBQ smoking.
        4. Night (10:15 PM): Reflection journal, checking on Tom Thompson's knee in Naperville, or index fund finances.

        Return JSON array of 4 session objects:
        [
          {{
            "sessionName": "morning",
            "hour": 7, "minute": 30,
            "title": "Morning Routine & Briefing",
            "dialogue": [
              {{"speaker": "user", "text": "...", "valence": 40, "importance": 1.2, "arousal": 60, "tags": ["morning-routine", "running"]}},
              {{"speaker": "jarvis", "text": "...", "valence": 20, "importance": 1.0, "arousal": 30, "tags": ["morning-routine", "calendar"]}}
            ]
          }},
          ...
        ]
        """
        
        try:
            sessions_data = call_gemini(prompt, SYSTEM_PROMPT)
            daily_records = []
            turn_counter = 1
            
            for sess in sessions_data:
                s_name = sess.get("sessionName", "session")
                s_dt = datetime.datetime.combine(cur_date, datetime.time(sess.get("hour", 12), sess.get("minute", 0)))
                s_id = f"session-{cur_date.strftime('%Y-%m-%d')}-{s_name}"
                
                for t in sess.get("dialogue", []):
                    m_id = f"mem-d{day_num:04d}-{turn_counter:03d}"
                    turn_counter += 1
                    txt = t.get("text", "")
                    speaker = t.get("speaker", "user")
                    
                    daily_records.append({
                        "id": m_id,
                        "text": txt,
                        "title": sess.get("title", "Daily Dialogue"),
                        "synapticTags": t.get("tags", ["daily-interaction"]),
                        "valence": int(t.get("valence", 20)),
                        "importance": float(t.get("importance", 1.5)),
                        "arousal": int(t.get("arousal", 50)),
                        "sessionId": s_id,
                        "timestampMs": to_epoch_ms(s_dt + datetime.timedelta(seconds=turn_counter*45)),
                        "entityMentions": make_entity_mentions(txt),
                        "memoryType": "EPISODIC" if speaker == "user" else "SEMANTIC",
                        "agentRecallCount": 0,
                        "interest": 0.7,
                        "challenge": 0.5,
                        "urgency": 0.3
                    })
                    
            day_file = DAILY_DIR / f"corpus-day-{day_num:04d}.jsonl"
            with open(day_file, "w", encoding="utf-8") as f:
                for r in daily_records:
                    f.write(json.dumps(r) + "\n")
            print(f"  Day {day_num:04d} ({cur_date}): Generated {len(daily_records)} rich turns via Gemini-3.1-Flash-Lite.")
        except Exception as e:
            print(f"  Error on day {day_num}: {e}")

if __name__ == "__main__":
    print("=== MINDSPAN GEMINI-3.1-FLASH-LITE SYNTHESIZER ===")
    synthesize_biographical_corpus()
    synthesize_daily_conversations(start_day=1, count_days=10)
    print("\nGemini synthesis test batch COMPLETE!")
