"""
Dedicated Test Harness for Evaluating Speculative & Commonsense Failed Queries in Isolation.
Target: Fixes Failure Mode A (Commonsense & Speculative Extrapolations).
"""

import os
import sys
import json
import argparse
import time
from typing import List, Dict, Any

# Ensure project root is on path
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(project_root, "scripts"))

from eval_generative_qa_ollama import (
    GENERATOR_SYSTEM_PROMPT,
    GENERATOR_PROMPT_TEMPLATE,
    JUDGE_SYSTEM_PROMPT,
    JUDGE_PROMPT_TEMPLATE,
    format_candidates_context,
    query_gemini,
    clean_thinking_traces
)

TARGET_SPECULATIVE_IDS = [
    # Top speculative failure cases identified from 387-query benchmark
    "q_conv_26_60",   # Would Caroline be considered religious? -> Somewhat, but not extremely religious
    "q_conv_26_70",   # What personality traits might Melanie say Caroline has? -> Thoughtful, authentic, driven
    "q_conv_41_9",    # What might John's financial status be? -> Middle-class or wealthy
    "q_conv_41_18",   # What might John's degree be in? -> Political science, Public administration, Public affairs
    "q_conv_41_65",   # What job might Maria pursue in the future? -> Shelter coordinator, Counselor
    "q_conv_41_42",   # Does John live close to a beach or the mountains? -> beach
    "q_conv_41_94",   # How has John's fitness improved since starting boot camps with his family? -> More energy, gains in strength and endurance
    "q_conv_41_106",  # How does John plan to honor the memories of his beloved pet? -> By considering adopting a rescue dog
    "q_conv_41_115",  # How does John describe the support he received during his journey to becoming assistant manager? -> having support at home and his own grit
    "q_conv_41_119",  # What inspired John to join the marching event for veterans' rights? -> Respect for the military and the desire to show support
    "q_conv_41_135",  # How did John describe his kids' reaction at the military memorial? -> awestruck and humbled
]

def main():
    parser = argparse.ArgumentParser(description="Test Speculative Failure Cases")
    parser.add_argument("--api-key", type=str, default=os.environ.get("GEMINI_API_KEY", "") or os.environ.get("GOOGLE_API_KEY", ""))
    parser.add_argument("--model", type=str, default="gemini-3.1-flash-lite")
    parser.add_argument("--dataset", type=str, default="locomo")
    args = parser.parse_args()

    api_key = args.api_key
    if not api_key:
        print("[ERROR] Gemini API key required. Provide via --api-key or GEMINI_API_KEY env var.")
        sys.exit(1)

    candidates_file = os.path.join(project_root, f"../spector-datasets/{args.dataset}/results/backup_387_queries_81pct/retrieved_candidates.jsonl")
    if not os.path.exists(candidates_file):
        candidates_file = os.path.join(project_root, f"../spector-datasets/{args.dataset}/results/retrieved_candidates.jsonl")

    print(f"Loading candidates from: {candidates_file}")
    records = [json.loads(l) for l in open(candidates_file, encoding="utf-8") if l.strip()]
    records_by_id = {r.get("query_id"): r for r in records}

    test_records = []
    for qid in TARGET_SPECULATIVE_IDS:
        if qid in records_by_id:
            test_records.append(records_by_id[qid])
        else:
            print(f"[WARN] Query ID {qid} not found in candidates file.")

    print(f"\nEvaluating {len(test_records)} speculative/commonsense test queries with model: {args.model}")
    print("=" * 80)

    passed_count = 0
    total_count = len(test_records)

    for idx, r in enumerate(test_records, 1):
        qid = r.get("query_id")
        q = r.get("question")
        gold = r.get("gold_answer")
        candidates = r.get("candidates", [])
        context = format_candidates_context(candidates, 50) if candidates else r.get("context_text", "")

        print(f"\n[{idx:02d}/{total_count}] Testing {qid}")
        print(f"Question: {q}")
        print(f"Gold:     {gold}")

        gen_prompt = GENERATOR_PROMPT_TEMPLATE.format(context=context, question=q)
        gen_ans = query_gemini(gen_prompt, api_key, model=args.model, system_instruction=GENERATOR_SYSTEM_PROMPT)
        gen_ans = clean_thinking_traces(gen_ans)

        print(f"Generated: {gen_ans}")

        judge_prompt = JUDGE_PROMPT_TEMPLATE.format(question=q, gold_answer=gold, predicted_answer=gen_ans)
        judge_raw = query_gemini(judge_prompt, api_key, model=args.model, system_instruction=JUDGE_SYSTEM_PROMPT, format_json=True)
        
        is_correct = False
        explanation = ""
        try:
            cleaned_json = clean_thinking_traces(judge_raw).strip()
            if "```json" in cleaned_json:
                cleaned_json = cleaned_json.split("```json")[1].split("```")[0].strip()
            elif "```" in cleaned_json:
                cleaned_json = cleaned_json.split("```")[1].split("```")[0].strip()
            jobj = json.loads(cleaned_json)
            is_correct = bool(jobj.get("correct", False))
            explanation = jobj.get("explanation", "")
        except Exception as e:
            explanation = f"JSON parse error: {e} | raw: {judge_raw[:100]}"

        status_str = "PASSED" if is_correct else "FAILED"
        print(f"Outcome:   {status_str} | Explanation: {explanation}")
        if is_correct:
            passed_count += 1

        time.sleep(0.5)

    print("\n" + "=" * 80)
    print(f"Speculative / Commonsense Accuracy: {passed_count}/{total_count} ({passed_count/total_count*100:.2f}%)")
    print(f"Previous Benchmark Accuracy on these queries: 0/{total_count} (0.00%)")
    print(f"Net Improvement: +{passed_count/total_count*100:.2f}%")
    print("=" * 80)

if __name__ == "__main__":
    main()
