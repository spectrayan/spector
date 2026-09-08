#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# Spector — One-Line POSIX Installer (macOS & Linux)
# ═══════════════════════════════════════════════════════════════════
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/spectrayan/spector/main/scripts/install.sh | sh
#
# Options:
#   --dry-run             Simulate installation without modifying system
#   --version <tag>       Install a specific release version (e.g. v0.1.0-alpha)
#   --install-dir <path>  Custom installation directory (default: ~/.spector)
# ═══════════════════════════════════════════════════════════════════

set -euo pipefail

REPO="spectrayan/spector"
SPECTOR_HOME="${HOME}/.spector"
TARGET_VERSION="latest"
DRY_RUN=0
FORCE=0

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --force|-f)
      FORCE=1
      shift
      ;;
    --version)
      TARGET_VERSION="$2"
      shift 2
      ;;
    --install-dir)
      SPECTOR_HOME="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1"
      exit 1
      ;;
  esac
done

BIN_DIR="${SPECTOR_HOME}/bin"
JAR_PATH="${BIN_DIR}/spector.jar"
WRAPPER_PATH="${BIN_DIR}/spector"

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║             Spector Cognitive Memory & CLI Installer              ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"

# 1. Inspect Environment
OS="$(uname -s)"
ARCH="$(uname -m)"
echo "→ System detected: ${OS} (${ARCH})"

# 2. Check Java Runtime
HAS_JAVA25=0
if command -v java >/dev/null 2>&1; then
  JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' || true)
  echo "→ Detected Java version: ${JAVA_VER:-unknown}"
  if [[ "${JAVA_VER}" =~ ^2[5-9] ]] || [[ "${JAVA_VER}" =~ ^[3-9][0-9] ]]; then
    HAS_JAVA25=1
  fi
fi

if [[ ${HAS_JAVA25} -eq 0 ]]; then
  echo "⚠️  OpenJDK 25 with Vector API was not detected on your PATH."
  echo "   Spector requires JDK 25+ to leverage SIMD vector hardware acceleration."
  echo "   Recommended installation commands:"
  if [[ "${OS}" == "Darwin" ]]; then
    echo "     brew install openjdk@25"
  else
    echo "     sudo apt install openjdk-25-jdk  # Ubuntu/Debian"
    echo "     sudo dnf install java-25-openjdk # Fedora/RHEL"
  fi
  if [[ ${FORCE} -eq 0 ]]; then
    echo "❌ Error: OpenJDK 25+ is required to install and run Spector. Install Java 25 or pass --force to bypass this check." >&2
    exit 1
  fi
fi

# 4. Fetch Release Asset
if [[ "${TARGET_VERSION}" == "latest" ]]; then
  API_URL="https://api.github.com/repos/${REPO}/releases/latest"
else
  TAG="${TARGET_VERSION}"
  [[ "${TAG}" != v* ]] && TAG="v${TAG}"
  API_URL="https://api.github.com/repos/${REPO}/releases/tags/${TAG}"
fi

echo "→ Querying GitHub Releases (${API_URL})..."
RELEASE_JSON=$(curl -fsSL -H "User-Agent: spector-installer" -H "Accept: application/vnd.github.v3+json" "${API_URL}" 2>/dev/null || true)

if [[ -z "${RELEASE_JSON}" ]]; then
  echo "❌ Error: Could not retrieve release metadata from ${API_URL}." >&2
  echo "   Verify network connectivity, or specify an existing release via --version <tag>." >&2
  exit 1
fi

parse_asset_url() {
  local asset_name="$1"
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "import sys, json; data=json.loads(sys.argv[1]); assets=data.get('assets', []); print(next((a.get('browser_download_url', '') for a in assets if a.get('name') == '$asset_name'), ''))" "${RELEASE_JSON}" 2>/dev/null || true
  elif command -v jq >/dev/null 2>&1; then
    jq -r --arg name "$asset_name" '.assets[]? | select(.name == $name) | .browser_download_url' <<< "${RELEASE_JSON}" 2>/dev/null || true
  else
    grep -o "https://[^\"]*releases/download/[^\"]*/${asset_name}" <<< "${RELEASE_JSON}" | head -n 1 || true
  fi
}

JAR_ASSET_URL=$(parse_asset_url "spector.jar")
SHA_ASSET_URL=$(parse_asset_url "spector.jar.sha256")

if [[ ${DRY_RUN} -eq 1 ]]; then
  echo "→ [DRY-RUN] Target directory: ${BIN_DIR}"
  echo "→ [DRY-RUN] Target JAR path:  ${JAR_PATH}"
  echo "→ [DRY-RUN] Wrapper script:   ${WRAPPER_PATH}"
  echo "→ [DRY-RUN] Release API URL:  ${API_URL}"
  echo "→ [DRY-RUN] Java 25 status:   $([[ ${HAS_JAVA25} -eq 1 ]] && echo 'FOUND' || echo 'NOT FOUND (JDK 25 required)')"
  if [[ -z "${JAR_ASSET_URL}" ]]; then
    echo "❌ [DRY-RUN] Release asset 'spector.jar' was not found on GitHub Releases!" >&2
    exit 1
  fi
  echo "→ [DRY-RUN] Release asset 'spector.jar' verified: ${JAR_ASSET_URL}"
  echo "→ [DRY-RUN] Verification complete. Exiting dry run."
  exit 0
fi

if [[ -z "${JAR_ASSET_URL}" ]]; then
  echo "❌ Error: Release asset 'spector.jar' was not found in release (${API_URL})." >&2
  exit 1
fi

# 3. Create Directories
mkdir -p "${BIN_DIR}" "${SPECTOR_HOME}/data"

# 4. Download and Verify
echo "→ Downloading spector.jar from ${JAR_ASSET_URL}..."
curl -fsSL -H "User-Agent: spector-installer" -o "${JAR_PATH}" "${JAR_ASSET_URL}"

if [[ -n "${SHA_ASSET_URL}" ]]; then
  echo "→ Downloading spector.jar.sha256..."
  SHA_FILE="${BIN_DIR}/spector.jar.sha256"
  curl -fsSL -H "User-Agent: spector-installer" -o "${SHA_FILE}" "${SHA_ASSET_URL}"
  EXPECTED_HASH=$(awk '{print $1}' "${SHA_FILE}" | tr -d '\r\n')

  ACTUAL_HASH=""
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_HASH=$(sha256sum "${JAR_PATH}" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_HASH=$(shasum -a 256 "${JAR_PATH}" | awk '{print $1}')
  elif command -v python3 >/dev/null 2>&1; then
    ACTUAL_HASH=$(python3 -c "import hashlib; print(hashlib.sha256(open('${JAR_PATH}','rb').read()).hexdigest())")
  fi

  if [[ -n "${ACTUAL_HASH}" ]]; then
    if [[ "${ACTUAL_HASH}" != "${EXPECTED_HASH}" ]]; then
      echo "❌ SHA-256 verification failed! Expected ${EXPECTED_HASH}, got ${ACTUAL_HASH}" >&2
      rm -f "${JAR_PATH}" "${SHA_FILE}"
      exit 1
    fi
    echo "→ Verified SHA-256: ${ACTUAL_HASH}"
  fi
fi

# 5. Create Executable Wrapper
echo "→ Generating executable CLI wrapper at ${WRAPPER_PATH}..."
cat > "${WRAPPER_PATH}" << 'EOF'
#!/usr/bin/env bash
set -e
SPECTOR_HOME="${SPECTOR_HOME:-$HOME/.spector}"
JAR="${SPECTOR_HOME}/bin/spector.jar"

if [[ ! -f "$JAR" ]]; then
  echo "Error: $JAR not found. Re-run installer to download spector.jar." >&2
  exit 1
fi

JAVA_CMD="java"
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_CMD="${JAVA_HOME}/bin/java"
fi

# Check Java 25
JAVA_VER=$("${JAVA_CMD}" -version 2>&1 | awk -F '"' '/version/ {print $2}' || true)
if [[ ! "${JAVA_VER}" =~ ^2[5-9] ]] && [[ ! "${JAVA_VER}" =~ ^[3-9][0-9] ]]; then
  echo "Error: Spector requires OpenJDK 25+. Detected: ${JAVA_VER:-unknown}" >&2
  echo "Install OpenJDK 25 via: brew install openjdk@25 (macOS) or sudo apt install openjdk-25-jdk (Linux)" >&2
  exit 1
fi

exec "${JAVA_CMD}" \
  --enable-preview \
  --add-modules=jdk.incubator.vector \
  --enable-native-access=ALL-UNNAMED \
  -jar "$JAR" "$@"
EOF
chmod +x "${WRAPPER_PATH}"

# 6. Configure Shell Environment
ENV_FILE="${SPECTOR_HOME}/env"
cat > "${ENV_FILE}" << EOF
export SPECTOR_HOME="${SPECTOR_HOME}"
export PATH="${BIN_DIR}:\$PATH"
EOF

SHELL_UPDATED=0
for RC in "${HOME}/.bashrc" "${HOME}/.zshrc"; do
  if [[ -f "${RC}" ]] && ! grep -q 'SPECTOR_HOME' "${RC}"; then
    echo "" >> "${RC}"
    echo "# Spector Cognitive Memory" >> "${RC}"
    echo "[[ -f \"${ENV_FILE}\" ]] && source \"${ENV_FILE}\"" >> "${RC}"
    SHELL_UPDATED=1
  fi
done

echo ""
echo "✅ Spector CLI successfully installed to: ${WRAPPER_PATH}"
echo "ℹ️  Environment file written to: ${ENV_FILE}"
if [[ ${SHELL_UPDATED} -eq 1 ]]; then
  echo "ℹ️  Shell configuration updated. Restart terminal or run: source ${ENV_FILE}"
fi
echo ""
echo "Test installation with:"
echo "  spector doctor"
echo ""
