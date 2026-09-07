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

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=1
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
  echo "   (Installation will continue, but ensure JDK 25 is installed before execution.)"
fi

if [[ ${DRY_RUN} -eq 1 ]]; then
  echo "→ [DRY-RUN] Target directory: ${BIN_DIR}"
  echo "→ [DRY-RUN] Wrapper script: ${WRAPPER_PATH}"
  echo "→ [DRY-RUN] Verification complete. Exiting dry run."
  exit 0
fi

# 3. Create Directories
mkdir -p "${BIN_DIR}" "${SPECTOR_HOME}/data"

# 4. Fetch Release Asset
if [[ "${TARGET_VERSION}" == "latest" ]]; then
  API_URL="https://api.github.com/repos/${REPO}/releases/latest"
else
  API_URL="https://api.github.com/repos/${REPO}/releases/tags/${TARGET_VERSION}"
fi

echo "→ Querying GitHub Releases (${API_URL})..."
ASSET_URL=$(curl -sSL "${API_URL}" | grep -o 'https://[^"]*spector\.jar' | head -n 1 || true)

if [[ -z "${ASSET_URL}" ]]; then
  echo "ℹ️  Release asset 'spector.jar' not found on GitHub Releases."
  echo "   Creating installation directory and launcher shim for local usage."
else
  echo "→ Downloading spector.jar..."
  curl -fsSL -o "${JAR_PATH}" "${ASSET_URL}"
fi

# 5. Create Executable Wrapper
echo "→ Generating executable CLI wrapper at ${WRAPPER_PATH}..."
cat > "${WRAPPER_PATH}" << 'EOF'
#!/usr/bin/env bash
SPECTOR_HOME="${SPECTOR_HOME:-$HOME/.spector}"
JAR="${SPECTOR_HOME}/bin/spector.jar"

if [[ ! -f "$JAR" ]]; then
  echo "Error: $JAR not found. Build or download spector.jar first."
  exit 1
fi

exec java \
  --enable-preview \
  --add-modules=jdk.incubator.vector \
  --enable-native-access=ALL-UNNAMED \
  -jar "$JAR" "$@"
EOF
chmod +x "${WRAPPER_PATH}"

# 6. Configure Shell PATH
SHELL_UPDATED=0
for RC in "${HOME}/.bashrc" "${HOME}/.zshrc"; do
  if [[ -f "${RC}" ]] && ! grep -q 'SPECTOR_HOME' "${RC}"; then
    echo "" >> "${RC}"
    echo "# Spector CLI" >> "${RC}"
    echo 'export SPECTOR_HOME="'"${SPECTOR_HOME}"'"' >> "${RC}"
    echo 'export PATH="${SPECTOR_HOME}/bin:$PATH"' >> "${RC}"
    SHELL_UPDATED=1
  fi
done

echo ""
echo "✅ Spector CLI successfully installed to: ${WRAPPER_PATH}"
if [[ ${SHELL_UPDATED} -eq 1 ]]; then
  echo "ℹ️  Shell configuration updated. Restart your terminal or run:"
  echo "     export PATH=\"${BIN_DIR}:\$PATH\""
fi
echo ""
echo "Quick Test:"
echo "  spector doctor"
echo ""
