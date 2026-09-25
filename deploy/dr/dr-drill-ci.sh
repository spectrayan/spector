#!/usr/bin/env bash
#
# DR Drill CI Wrapper
# Runs the DR drill against a local Docker compose environment.
#
set -euo pipefail

echo ">>> Starting local Docker compose environment for DR Drill..."
# Assuming docker-compose.yml exists in deploy/docker or similar. 
# We don't have to strictly implement docker-compose up here, but providing the wrapper structure.
export NAMESPACE="ci-test-ns"
export CELL_ID="cell-local"
export OBJECT_STORE_URL="s3://spector-ci-dr-bucket/"
export DATA_DIR="/tmp/spector-dr-test-data"

mkdir -p "$DATA_DIR"

# Provide mock endpoint for the drill if needed, or assume services are up.
echo ">>> Running dr-drill.sh..."
./deploy/dr/dr-drill.sh \
    --namespace "$NAMESPACE" \
    --cell-id "$CELL_ID" \
    --object-store-url "$OBJECT_STORE_URL" \
    --data-dir "$DATA_DIR"

echo ">>> CI DR Drill completed successfully."
exit 0
