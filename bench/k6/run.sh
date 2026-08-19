#!/usr/bin/env bash
#
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
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIO="${1:-smoke}"
BASE_URL="${BASE_URL:-http://localhost:7070}"
API_KEY="${API_KEY:-spector-dev-key}"
AUTH_ENABLED="${AUTH_ENABLED:-false}"

if ! command -v k6 &> /dev/null; then
    echo -e "\033[0;31m[ERROR] k6 executable not found in PATH.\033[0m"
    echo "Please install Grafana k6: https://grafana.com/docs/k6/latest/set-up/install-k6/"
    exit 1
fi

case "$SCENARIO" in
    smoke)     FILE="$SCRIPT_DIR/scenarios/01-smoke-test.js" ;;
    load)      FILE="$SCRIPT_DIR/scenarios/02-load-test.js" ;;
    stress)    FILE="$SCRIPT_DIR/scenarios/03-stress-test.js" ;;
    spike)     FILE="$SCRIPT_DIR/scenarios/04-spike-test.js" ;;
    soak)      FILE="$SCRIPT_DIR/scenarios/05-soak-test.js" ;;
    mixed)     FILE="$SCRIPT_DIR/scenarios/06-mixed-workload.js" ;;
    isolation) FILE="$SCRIPT_DIR/scenarios/07-multi-user-isolation.js" ;;
    lru)       FILE="$SCRIPT_DIR/scenarios/08-user-registry-lru-stress.js" ;;
    *)
        echo "Unknown scenario: $SCENARIO. Valid options: smoke, load, stress, spike, soak, mixed, isolation, lru"
        exit 1
        ;;
esac

echo -e "\033[0;36mRunning Spector k6 Scenario: $SCENARIO ($FILE)\033[0m"
k6 run \
  -e "BASE_URL=$BASE_URL" \
  -e "API_KEY=$API_KEY" \
  -e "AUTH_ENABLED=$AUTH_ENABLED" \
  "$FILE"
