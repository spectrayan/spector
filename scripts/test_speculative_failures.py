"""
Dedicated Test Harness for Evaluating Failed Queries from Benchmark in Isolation.
Supports evaluating all 72 failing queries across Failure Modes A, B, C, and D.
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

def main():
    parser = argparse.ArgumentParser(description="Test Failed Benchmark Queries in Isolation")
    parser.add_argument("--api-key", type=str, default=os.environ.get("GEMINI_API_KEY", "") or os.environ.get("GOOGLE_API_KEY", ""))
    parser.add_argument("--model", type=str, default="gemini-3.1-flash-lite")
    parser.add_argument("--dataset", type=str, default="locomo")
    parser.add_argument("--all-failures", action="store_true", help="Evaluate all 72 failed queries from 387-query benchmark")
    parser.add_argument("--limit", type=int, default=0, help="Limit number of queries to test")
    args = parser.parse_args()

    api_key = args.api_key
    if not api_key:
        print("[ERROR] Gemini API key required. Provide via --api-key or GEMINI_API_KEY env var.")
        sys.exit(1)

    eval_checkpoint = os.path.join(project_root, f"../spector-datasets/{args.dataset}/results/backup_387_queries_81pct/qa_eval_checkpoint.jsonl")
    if not os.path.exists(eval_checkpoint):
        eval_checkpoint = os.path.join(project_root, f"../spector-datasets/{args.dataset}/results/qa_eval_checkpoint.jsonl")

    candidates_file = os.path.join(project_root, f"../spector-datasets/{args.dataset}/results/backup_387_queries_81pct/retrieved_candidates.jsonl")
    if not os.path.exists(candidates_file):
        candidates_file = os.path.join(project_root, f"../spector-datasets/{args.dataset}/results/retrieved_candidates.jsonl")

    print(f"Loading evaluated benchmark records from: {eval_checkpoint}")
    eval_records = [json.loads(l) for l in open(eval_checkpoint, encoding="utf-8") if l.strip()]
    eval_map = {r.get("query_id"): r for r in eval_records}

    print(f"Loading candidates from: {candidates_file}")
    cand_records = [json.loads(l) for l in open(candidates_file, encoding="utf-8") if l.strip()]
    cand_map = {r.get("query_id"): r for r in cand_records}

    # Identify target query IDs
    if args.all_failures:
        target_ids = [r.get("query_id") for r in eval_records if not r.get("is_correct")]
        print(f"Targeting ALL {len(target_ids)} failed queries from the 387-query benchmark.")
    else:
        target_ids = [
            "q_conv_26_60", "q_conv_26_70", "q_conv_41_9", "q_conv_41_18",
            "q_conv_41_65", "q_conv_41_42", "q_conv_41_94", "q_conv_41_106",
            "q_conv_41_115", "q_conv_41_119", "q_conv_41_135"
        ]

    test_records = []
    for qid in target_ids:
        if qid in cand_map:
            test_records.append(cand_map[qid])
        elif qid in eval_map:
            test_records.append(eval_map[qid])
        else:
            print(f"[WARN] Query ID {qid} not found.")

    if args.limit > 0:
        test_records = test_records[:args.limit]

    print(f"\nEvaluating {len(test_records)} failed test queries with model: {args.model}")
    print("=" * 80)

    passed_count = 0
    total_count = len(test_records)
    results = []

    for idx, r in enumerate(test_records, 1):
        qid = r.get("query_id")
        q = r.get("question")
        gold = r.get("gold_answer")
        candidates = r.get("candidates", [])
        context = format_candidates_context(candidates, 50) if candidates else r.get("context_text", "")
        prev_ans = eval_map.get(qid, {}).get("predicted_answer", "")

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

        results.append({
            "query_id": qid,
            "question": q,
            "gold": gold,
            "generated": gen_ans,
            "is_correct": is_correct,
            "explanation": explanation
        })

        time.sleep(0.3)

    print("\n" + "=" * 80)
    print(f"Re-Evaluation Accuracy on Failed Set: {passed_count}/{total_count} ({passed_count/total_count*100:.2f}%)")
    print(f"Previous Benchmark Accuracy on this set: 0/{total_count} (0.00%)")
    print(f"Net Recovered Queries: +{passed_count} / {total_count} (+{passed_count/total_count*100:.2f}%)")
    print("=" * 80)

if __name__ == "__main__":
    main()
