#!/usr/bin/env bash
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

set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# Spector Single-Namespace Scale Benchmark Runner (Milestone 6 / R6)
# ─────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

TIER="100k"
BUDGET=10
OUTPUT=""
RUN_K6=false
FULL_MODE=""

usage() {
    cat <<EOF
Usage: $(basename "$0") [options]

Options:
  --tier <smoke|100k|1m|10m|all>  Scale tier to benchmark (default: 100k)
  --budget <num>                  Partition visit budget cap (default: 10)
  --output <dir>                  Directory to write Markdown report
  --full                          Run live 1M/10M ingestion instead of extrapolation
  --k6                            Execute Grafana k6 REST API scenario instead of direct JVM benchmark
  -h, --help                      Show this help message
EOF
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --tier)
            TIER="$2"
            shift 2
            ;;
        --budget)
            BUDGET="$2"
            shift 2
            ;;
        --output)
            OUTPUT="$2"
            shift 2
            ;;
        --full)
            FULL_MODE="--full"
            shift
            ;;
        --k6)
            RUN_K6=true
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            ;;
    esac
done

if [ "$RUN_K6" = true ]; then
    echo "▶ Launching k6 Single-Namespace Scale Scenario (${TIER}, budget=${BUDGET})..."
    export SCALE_TIER="${TIER}"
    export VISIT_BUDGET="${BUDGET}"
    k6 run "${SCRIPT_DIR}/k6/scenarios/09-single-namespace-scale.js"
    exit 0
fi

echo "▶ Building spector-bench dependencies if needed..."
cd "${ROOT_DIR}"
mvn compile test-compile -pl bench/spector-bench -DskipTests -q

CP=$(mvn -pl bench/spector-bench dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)
CLASSPATH="${ROOT_DIR}/bench/spector-bench/target/classes:${ROOT_DIR}/bench/spector-bench/target/test-classes:${CP}"

OUTPUT_ARG=""
if [ -n "$OUTPUT" ]; then
    OUTPUT_ARG="--output=${OUTPUT}"
fi

echo "▶ Executing SingleNamespaceScaleBenchmark (tier=${TIER}, budget=${BUDGET})..."
java \
    --enable-preview \
    --add-modules jdk.incubator.vector \
    --enable-native-access=ALL-UNNAMED \
    -Xmx4g \
    -cp "${CLASSPATH}" \
    com.spectrayan.spector.bench.scale.SingleNamespaceScaleBenchmark \
    "--tier=${TIER}" \
    "--budget=${BUDGET}" \
    ${OUTPUT_ARG} \
    ${FULL_MODE}
