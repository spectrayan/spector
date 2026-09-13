#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# Spector AOT Cache Generator (JDK 27 / JEP 516 / JEP 534)
# Generates Leyden AOT configuration and object cache compatible with ZGC
# ═══════════════════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

JAR_PATH="${ROOT_DIR}/synapse/spector-cli/target/spector.jar"
AOT_CONFIG="${ROOT_DIR}/synapse/spector-cli/target/spector.aotconfig"
AOT_CACHE="${ROOT_DIR}/synapse/spector-cli/target/spector.aot"

JAVA_CMD="java"
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_CMD="${JAVA_HOME}/bin/java"
fi

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║          Spector AOT Object Cache Generator (JDK 27)             ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"

# 1. Verify Java Version
JAVA_VER=$("${JAVA_CMD}" -version 2>&1 | awk -F '"' '/version/ {print $2}' || true)
echo "→ Detected Java version: ${JAVA_VER:-unknown}"

if [[ ! "${JAVA_VER}" =~ ^2[7-9] ]] && [[ ! "${JAVA_VER}" =~ ^[3-9][0-9] ]]; then
  echo "⚠️  Warning: AOT Object Caching with Any GC (JEP 516) requires JDK 27+."
  echo "   Current Java version: ${JAVA_VER}"
fi

# 2. Check Fat JAR
if [[ ! -f "${JAR_PATH}" ]]; then
  echo "→ Building spector-cli executable JAR..."
  (cd "${ROOT_DIR}" && mvn clean package -pl synapse/spector-cli -am -DskipTests -Dspotless.check.skip=true -Drat.skip=true)
fi

echo "→ Fat JAR located: ${JAR_PATH}"

JVM_COMMON_ARGS=(
  "--enable-preview"
  "--add-modules=jdk.incubator.vector"
  "--enable-native-access=ALL-UNNAMED"
  "-XX:+UseCompactObjectHeaders"
)

# 3. Phase 1: Record Training Profile
echo ""
echo "=== Phase 1: Recording Training Profile (${AOT_CONFIG}) ==="
"${JAVA_CMD}" \
  "${JVM_COMMON_ARGS[@]}" \
  -XX:AOTMode=record \
  -XX:AOTConfiguration="${AOT_CONFIG}" \
  -jar "${JAR_PATH}" --help > /dev/null 2>&1 || true

if [[ -f "${AOT_CONFIG}" ]]; then
  echo "✓ Training profile recorded: $(du -h "${AOT_CONFIG}" | cut -f1)"
else
  echo "❌ Error: Failed to generate ${AOT_CONFIG}" >&2
  exit 1
fi

# 4. Phase 2: Create AOT Object Cache Archive
echo ""
echo "=== Phase 2: Creating AOT Cache Archive (${AOT_CACHE}) ==="
"${JAVA_CMD}" \
  "${JVM_COMMON_ARGS[@]}" \
  -XX:AOTMode=create \
  -XX:AOTConfiguration="${AOT_CONFIG}" \
  -XX:AOTCache="${AOT_CACHE}" \
  -jar "${JAR_PATH}" > /dev/null 2>&1 || true

if [[ -f "${AOT_CACHE}" ]]; then
  echo "✓ AOT object cache created: $(du -h "${AOT_CACHE}" | cut -f1)"
else
  echo "❌ Error: Failed to generate ${AOT_CACHE}" >&2
  exit 1
fi

# 5. Phase 3: Benchmark Cold Startup with ZGC + AOT
echo ""
echo "=== Phase 3: Benchmarking Cold Startup ==="
echo "→ Running baseline (without AOT)..."
TIME_BEFORE=$({ time "${JAVA_CMD}" "${JVM_COMMON_ARGS[@]}" -XX:+UseZGC -jar "${JAR_PATH}" --help > /dev/null; } 2>&1 | grep real | awk '{print $2}')
echo "  Baseline cold startup: ${TIME_BEFORE}"

echo "→ Running with AOT Cache + ZGC..."
TIME_AOT=$({ time "${JAVA_CMD}" "${JVM_COMMON_ARGS[@]}" -XX:+UseZGC -XX:AOTCache="${AOT_CACHE}" -jar "${JAR_PATH}" --help > /dev/null; } 2>&1 | grep real | awk '{print $2}')
echo "  AOT-accelerated startup: ${TIME_AOT}"

echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "✓ AOT object cache successfully established at: ${AOT_CACHE}"
echo "  To launch Spector with AOT caching:"
echo "    java ${JVM_COMMON_ARGS[*]} -XX:+UseZGC -XX:AOTCache=${AOT_CACHE} -jar spector.jar"
echo "═══════════════════════════════════════════════════════════════════"
