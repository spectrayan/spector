#!/usr/bin/env python3
"""
Fast Smoke-Test QA Benchmark Runner
Evaluates 50 representative queries (excluding Category 5) using Gemini-3.1-Flash-Lite
for rapid turnaround (~15-20s).
"""

import argparse
import json
import os
import subprocess
import sys
import time

def main():
    parser = argparse.ArgumentParser(description="Fast Smoke Test QA Benchmark Runner")
    parser.add_argument("--sample-size", type=int, default=50, help="Number of queries to evaluate (default: 50)")
    parser.add_argument("--dataset", type=str, default="locomo", help="Dataset name")
    parser.add_argument("--top-k", type=int, default=50, help="Retrieval Top-K (default: 50)")
    parser.add_argument("--generator-model", type=str, default="gemini-3.1-flash-lite")
    parser.add_argument("--judge-model", type=str, default="gemini-3.1-flash-lite")
    parser.add_argument("--api-key", type=str, default=os.environ.get("GEMINI_API_KEY", "") or os.environ.get("GOOGLE_API_KEY", ""))
    parser.add_argument("--export-only", action="store_true", help="Only run candidate export")
    parser.add_argument("--eval-only", action="store_true", help="Only run evaluation on existing candidates")
    parser.add_argument("--fresh", action="store_true", help="Clear previous evaluation checkpoint")
    args = parser.parse_args()

    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    results_dir = os.path.join(project_root, f"../spector-datasets/{args.dataset}/results")
    os.makedirs(results_dir, exist_ok=True)

    candidates_file = os.path.join(results_dir, "retrieved_candidates.jsonl")
    checkpoint_file = os.path.join(results_dir, "qa_eval_checkpoint.jsonl")
    if args.fresh and os.path.exists(checkpoint_file):
        try:
            os.remove(checkpoint_file)
        except Exception:
            pass

    # Step 1: Candidate Export via ContextExportTest
    if not args.eval_only:
        print(f"\n[1/2] Exporting candidates for {args.sample_size} queries...")
        export_cmd = [
            "mvn", "test", "-pl", "bench/spector-bench", "-am", "-o",
            "-Dtest=ContextExportTest",
            "-Dsurefire.failIfNoSpecifiedTests=false",
            "-DskipBenchTests=false",
            f"-Ddataset={args.dataset}",
            f"-DtopK={args.top_k}",
            f"-Dlimit={args.sample_size}",
            "-Dspector.memory.graphExpansionMode=ALWAYS",
            f"-DoutputFile={candidates_file}"
        ]
        start_t = time.time()
        ret = subprocess.run(" ".join(export_cmd), shell=True, cwd=project_root)
        if ret.returncode != 0:
            print("[ERROR] ContextExportTest failed!")
            sys.exit(1)
        print(f"Export completed in {time.time() - start_t:.2f}s")

    if args.export_only:
        print("Export-only completed.")
        return

    # Step 2: Run Generative QA Evaluation
    print(f"\n[2/2] Evaluating {args.sample_size} queries with {args.generator_model} (Top-K Context: {args.top_k})...")
    eval_script = os.path.join(project_root, "scripts/eval_generative_qa_ollama.py")
    eval_cmd = [
        sys.executable, eval_script,
        "--candidates-file", candidates_file,
        "--generator-model", args.generator_model,
        "--judge-model", args.judge_model,
        "--limit", str(args.sample_size),
        "--top-k-context", str(args.top_k),
        "--fresh",
        "--gemini-api-key", args.api_key,
        "--output-dir", results_dir
    ]
    start_t = time.time()
    ret = subprocess.run(eval_cmd, cwd=project_root)
    if ret.returncode != 0:
        print("[ERROR] Evaluation failed!")
        sys.exit(1)
    print(f"Smoke test evaluation finished in {time.time() - start_t:.2f}s")

if __name__ == "__main__":
    main()
