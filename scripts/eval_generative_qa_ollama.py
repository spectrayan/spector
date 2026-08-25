#!/usr/bin/env python3
"""
Spector Memory — End-to-End Generative QA (J-Score) Evaluation Harness using Local Ollama.

Evaluates downstream question-answering accuracy using retrieved memory context
from Spector Memory. Measures J-Score (LLM-as-a-Judge answer accuracy %)
while isolating pure memory search latency (ms) from LLM generation time (s).

Features:
- Full model configurability (defaults to glm-4.7-flash:latest or user-specified model).
- Automatic reasoning/thinking trace extraction (strips <think>...</think> and Thinking... blocks).
- Structured memory formatting with relative timestamps and candidate indices.
- Paced sequential invocation with configurable delays (--delay-ms) to prevent thermal/resource starvation.
- Real-time JSONL checkpointing with resume capability.
- Multi-category accuracy breakdown (Single-hop, Multi-hop, Temporal, Open-domain).
- Comprehensive Markdown and JSON reporting.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.request
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional

# Ensure UTF-8 output on Windows consoles
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

GENERATOR_PROMPT_TEMPLATE = """You are an AI assistant answering questions about a user's multi-session history based on the provided retrieved memories.

Context Memories from User History:
{context}

Question: {question}
Provide a direct, concise factual answer to the question using the memories above:
Answer:"""

JUDGE_PROMPT_TEMPLATE = """You are an impartial evaluation judge grading an agent memory retrieval answer.

Question: {question}
Reference Ground-Truth Answer: {gold_answer}
Candidate Model Answer: {predicted_answer}

Grading Rules:
1. The candidate model answer is CORRECT if it contains the core factual information specified in the reference ground-truth answer.
2. Minor differences in phrasing, word order, or extra conversational context must NOT be penalized unless they directly contradict the reference answer.
3. If the candidate model answer states the correct fact (e.g. correct name, item, date, or number), mark "correct": true.

Respond strictly in valid JSON format:
{{
  "correct": true,
  "confidence": 1.0,
  "explanation": "concise rationale"
}}"""

def clean_thinking_traces(text: str) -> str:
    """Strip out reasoning/thinking tokens (e.g. <think>...</think> or Thinking... ...done thinking.)"""
    if not text:
        return ""
    # Strip <think>...</think>
    cleaned = re.sub(r'<think>.*?</think>', '', text, flags=re.DOTALL)
    # Strip Thinking... ...done thinking.
    cleaned = re.sub(r'Thinking\.\.\..*?\.\.\.done thinking\.', '', cleaned, flags=re.DOTALL)
    return cleaned.strip()

def query_ollama(
    url: str,
    prompt: str,
    model: str = "glm-4.7-flash:latest",
    format_json: bool = False,
    timeout: int = 120
) -> str:
    """Send a generate request to local Ollama API."""
    payload = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": 0.0,
            "top_p": 0.9
        }
    }
    if format_json:
        payload["format"] = "json"

    req = urllib.request.Request(
        f"{url.rstrip('/')}/api/generate",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        raw_resp = data.get("response", "").strip()
        return clean_thinking_traces(raw_resp) if not format_json else raw_resp

def parse_judge_response(raw_resp: str) -> bool:
    """Parse JSON boolean from judge response."""
    cleaned = clean_thinking_traces(raw_resp)
    try:
        data = json.loads(cleaned)
        if isinstance(data, dict):
            val = data.get("correct")
            if isinstance(val, bool):
                return val
            if isinstance(val, str):
                return val.lower() in ("true", "1", "yes", "correct")
    except Exception:
        pass
    # Fallback heuristic
    lower = cleaned.lower()
    if '"correct": true' in lower or '"correct":true' in lower:
        return True
    if '"correct": false' in lower or '"correct":false' in lower:
        return False
    return "true" in lower and "false" not in lower

def load_checkpoint(checkpoint_file: str) -> Dict[str, Dict[str, Any]]:
    """Load completed judgements from checkpoint JSONL."""
    completed = {}
    if os.path.exists(checkpoint_file):
        with open(checkpoint_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        record = json.loads(line)
                        qid = record.get("query_id")
                        if qid:
                            completed[qid] = record
                    except Exception:
                        pass
    return completed

def format_candidates_context(candidates: List[Dict[str, Any]], top_k: int = 5) -> str:
    """Format candidate memories into a clean prompt context block."""
    lines = []
    selected = candidates[:top_k] if top_k > 0 else candidates
    for i, c in enumerate(selected):
        text = c.get("text", "").strip()
        age = c.get("ageDays", 0.0)
        lines.append(f"[{i+1}] (Recorded: {age:.1f} days ago)\n{text}")
    return "\n\n".join(lines)

def main():
    parser = argparse.ArgumentParser(description="Run End-to-End Generative QA Evaluation on Spector Memory using Local Ollama")
    parser.add_argument("--candidates-file", type=str, required=True, help="Path to retrieved_candidates.jsonl exported from Spector")
    parser.add_argument("--output-dir", type=str, default="", help="Output directory for reports (default: candidates-file directory)")
    parser.add_argument("--model", type=str, default="", help="Unified model name for both generation and judging")
    parser.add_argument("--generator-model", type=str, default="llama3.1:latest", help="Ollama model for answering questions")
    parser.add_argument("--judge-model", type=str, default="", help="Ollama model for grading answers (defaults to generator model)")
    parser.add_argument("--ollama-url", type=str, default="http://127.0.0.1:11434", help="Ollama endpoint URL")
    parser.add_argument("--delay-ms", type=int, default=500, help="Delay in milliseconds between Ollama calls (pacing/thermal stability)")
    parser.add_argument("--top-k-context", type=int, default=10, help="Number of top candidates to include in LLM context (default: 10)")
    parser.add_argument("--limit", type=int, default=0, help="Limit number of queries to evaluate (0 = all)")
    parser.add_argument("--resume", action="store_true", default=False, help="Resume from existing checkpoint file")
    parser.add_argument("--fresh", action="store_true", default=False, help="Start evaluation from scratch and overwrite checkpoint")

    args = parser.parse_args()

    # Synchronize models if unified --model is passed
    if args.model:
        args.generator_model = args.model
        args.judge_model = args.model
    elif not args.judge_model:
        args.judge_model = args.generator_model

    candidates_path = os.path.abspath(args.candidates_file)
    if not os.path.exists(candidates_path):
        print(f"Error: Candidates file not found: {candidates_path}", file=sys.stderr)
        sys.exit(1)

    out_dir = args.output_dir if args.output_dir else os.path.dirname(candidates_path)
    os.makedirs(out_dir, exist_ok=True)

    checkpoint_file = os.path.join(out_dir, "qa_eval_checkpoint.jsonl")
    report_file = os.path.join(out_dir, "qa_generative_report.md")
    matrix_file = os.path.join(out_dir, "qa_matrix.json")

    print("=" * 70)
    print(" Spector Memory -- End-to-End Generative QA (J-Score) Evaluator")
    print(f" Generator: {args.generator_model:<25} Judge: {args.judge_model:<25}")
    print(f" Delay: {args.delay_ms} ms | Top-K Context: {args.top_k_context} | Candidates: {os.path.basename(candidates_path)}")
    print("=" * 70)

    # Load candidates
    queries = []
    with open(candidates_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                queries.append(json.loads(line))

    if args.limit > 0:
        queries = queries[:args.limit]
        print(f"Limiting evaluation to first {len(queries)} queries.")

    use_resume = args.resume and not args.fresh
    completed_map = load_checkpoint(checkpoint_file) if use_resume else {}
    if completed_map:
        print(f"Resuming evaluation: {len(completed_map)}/{len(queries)} queries already evaluated.")

    results = []
    checkpoint_writer = open(checkpoint_file, "a" if use_resume else "w", encoding="utf-8", buffering=1)

    category_stats: Dict[str, Dict[str, Any]] = {}
    total_recall_latency_ms = 0.0
    total_gen_time_s = 0.0
    total_judge_time_s = 0.0
    total_tokens = 0
    correct_count = 0

    try:
        for idx, item in enumerate(queries):
            qid = item.get("query_id", f"q_{idx}")
            question = item.get("question", "")
            gold_answer = item.get("gold_answer", "")
            candidates = item.get("candidates", [])
            context_text = format_candidates_context(candidates, args.top_k_context) if candidates else item.get("context_text", "")
            category = item.get("expected_subsystem", item.get("category", "GENERAL"))
            recall_latency_ms = float(item.get("recall_latency_ms", 0.0))
            context_tokens = max(1, len(context_text) // 4)

            if category not in category_stats:
                category_stats[category] = {"total": 0, "correct": 0, "tokens": 0, "recall_ms": 0.0}

            if qid in completed_map:
                record = completed_map[qid]
                is_correct = record.get("is_correct", False)
                gen_answer = record.get("predicted_answer", "")
                gen_s = record.get("generation_time_s", 0.0)
                judge_s = record.get("judge_time_s", 0.0)
            else:
                # 1. Generate Answer
                prompt = GENERATOR_PROMPT_TEMPLATE.format(context=context_text, question=question)
                t0 = time.perf_counter()
                try:
                    gen_answer = query_ollama(args.ollama_url, prompt, model=args.generator_model)
                except Exception as e:
                    gen_answer = f"[ERROR: Generation failed: {e}]"
                gen_s = time.perf_counter() - t0

                if args.delay_ms > 0:
                    time.sleep(args.delay_ms / 1000.0)

                # 2. Judge Answer
                judge_prompt = JUDGE_PROMPT_TEMPLATE.format(
                    question=question,
                    gold_answer=gold_answer,
                    predicted_answer=gen_answer
                )
                t1 = time.perf_counter()
                try:
                    judge_resp = query_ollama(args.ollama_url, judge_prompt, model=args.judge_model, format_json=True)
                    is_correct = parse_judge_response(judge_resp)
                except Exception as e:
                    judge_resp = f'{{"correct": false, "explanation": "{e}"}}'
                    is_correct = False
                judge_s = time.perf_counter() - t1

                record = {
                    "query_id": qid,
                    "question": question,
                    "gold_answer": gold_answer,
                    "predicted_answer": gen_answer,
                    "category": category,
                    "is_correct": is_correct,
                    "recall_latency_ms": recall_latency_ms,
                    "context_tokens": context_tokens,
                    "generation_time_s": gen_s,
                    "judge_time_s": judge_s,
                    "judge_response": judge_resp,
                    "timestamp": datetime.now(timezone.utc).isoformat()
                }

                checkpoint_writer.write(json.dumps(record) + "\n")

                if args.delay_ms > 0:
                    time.sleep(args.delay_ms / 1000.0)

            results.append(record)
            total_recall_latency_ms += recall_latency_ms
            total_gen_time_s += gen_s
            total_judge_time_s += judge_s
            total_tokens += context_tokens

            category_stats[category]["total"] += 1
            category_stats[category]["tokens"] += context_tokens
            category_stats[category]["recall_ms"] += recall_latency_ms
            if is_correct:
                correct_count += 1
                category_stats[category]["correct"] += 1

            status_sym = "[PASS]" if is_correct else "[FAIL]"
            current_q_num = len(results)
            cumulative_pct = (correct_count / current_q_num) * 100.0
            print(f"[{current_q_num:3d}/{len(queries):3d}] {status_sym} Correct: {correct_count:2d}/{current_q_num:2d} ({cumulative_pct:5.1f}%) | Memory: {recall_latency_ms:5.2f}ms | Gen: {gen_s:4.1f}s | Q: {question[:40]}...")

    finally:
        checkpoint_writer.close()

    total_q = len(results)
    if total_q == 0:
        print("No queries evaluated.")
        return

    overall_j_score = (correct_count / total_q) * 100.0
    avg_recall_ms = total_recall_latency_ms / total_q
    avg_tokens = total_tokens / total_q
    avg_gen_s = total_gen_time_s / total_q

    print("\n" + "=" * 70)
    print(f"EVALUATION COMPLETE: {total_q} Queries")
    print(f"Total Correct:                         {correct_count} / {total_q}")
    print(f"Overall Downstream J-Score (Accuracy): {overall_j_score:.2f}%")
    print(f"Pure Memory Recall Latency (Avg):      {avg_recall_ms:.2f} ms")
    print(f"Context Tokens Injected (Avg):         {avg_tokens:.0f} tokens")
    print(f"Downstream Generation Time (Avg):      {avg_gen_s:.2f} s")
    print("=" * 70)

    # Generate Markdown Report
    md_lines = [
        "# Spector Memory — End-to-End Generative QA (J-Score) Evaluation Report",
        "",
        f"> **Generated:** {datetime.now(timezone.utc).isoformat()}  ",
        f"> **Generator Model:** `{args.generator_model}` (Local Ollama)  ",
        f"> **Judge Model:** `{args.judge_model}` (Local Ollama)  ",
        f"> **Total Queries Evaluated:** {total_q}  ",
        "",
        "---",
        "",
        "## 1. Executive Summary & Headline Metrics",
        "",
        "| Metric | Spector Memory (Observed) | Zep (Published) | Mem0 (Published) |",
        "|:---|:---:|:---:|:---:|",
        f"| **Overall J-Score (QA Accuracy)** | **{overall_j_score:.2f}%** ({correct_count}/{total_q}) | 75.14% – 80.00% | 62.47% – 68.20% |",
        f"| **Pure Memory Search Latency** | **{avg_recall_ms:.2f} ms** | 632.0 ms | 657.0 ms |",
        f"| **Context Tokens Added** | **{avg_tokens:.0f} tokens** | 3,911 tokens | 1,764 tokens |",
        "",
        "> **Note on Latency Isolation:** Spector Memory search latency is measured strictly at the native off-heap recall layer. Local Ollama LLM inference latency is reported separately below and is not counted toward memory retrieval performance.",
        "",
        "---",
        "",
        "## 2. Category-Specific Breakdown",
        "",
        "| Reasoning Category / Subsystem | Evaluated Queries | Correct Answers | J-Score (Accuracy %) | Added Context Tokens | Pure Memory Latency (ms) |",
        "|:---|:---:|:---:|:---:|:---:|:---:|"
    ]

    matrix_categories = {}
    for cat, stats in sorted(category_stats.items()):
        c_tot = stats["total"]
        c_cor = stats["correct"]
        c_acc = (c_cor / c_tot * 100.0) if c_tot > 0 else 0.0
        c_tok = stats["tokens"] / c_tot if c_tot > 0 else 0.0
        c_lat = stats["recall_ms"] / c_tot if c_tot > 0 else 0.0

        matrix_categories[cat] = {
            "total_queries": c_tot,
            "correct": c_cor,
            "accuracy_pct": round(c_acc, 2),
            "avg_context_tokens": round(c_tok, 1),
            "avg_memory_latency_ms": round(c_lat, 2)
        }

        md_lines.append(f"| **{cat}** | {c_tot} | {c_cor} | **{c_acc:.2f}%** | {c_tok:.0f} | {c_lat:.2f} ms |")

    md_lines.extend([
        "",
        "---",
        "",
        "## 3. Latency & Resource Breakdown",
        "",
        "- **Spector Off-Heap Memory Recall:** Native Java 25 Panama Direct Memory (`HeaderLayout64`) + AVX-512 SIMD.",
        f"- **Average Memory Search Latency:** `{avg_recall_ms:.2f} ms`",
        f"- **Average LLM Generation Time (Ollama):** `{avg_gen_s:.2f} s`",
        f"- **Average Judge Time (Ollama):** `{total_judge_time_s / total_q:.2f} s`",
        f"- **Inter-Query Delay (Pacing):** `{args.delay_ms} ms`",
        "",
        "---",
        "*Report certified by Spectrayan Cognitive Systems & Benchmarking Team.*"
    ])

    with open(report_file, "w", encoding="utf-8") as f:
        f.write("\n".join(md_lines) + "\n")

    summary_json = {
        "evaluation_date": datetime.now(timezone.utc).isoformat(),
        "generator_model": args.generator_model,
        "judge_model": args.judge_model,
        "total_queries": total_q,
        "correct_answers": correct_count,
        "overall_j_score_pct": round(overall_j_score, 2),
        "avg_memory_latency_ms": round(avg_recall_ms, 2),
        "avg_context_tokens": round(avg_tokens, 1),
        "avg_generation_time_s": round(avg_gen_s, 2),
        "category_breakdown": matrix_categories
    }

    with open(matrix_file, "w", encoding="utf-8") as f:
        json.dump(summary_json, f, indent=2)

    print(f"Markdown report saved to: {report_file}")
    print(f"Summary JSON saved to:     {matrix_file}")

if __name__ == "__main__":
    main()
