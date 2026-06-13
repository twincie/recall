#!/usr/bin/env bash
set -euo pipefail

RECALL_VERSION="1.0.0"
REPO="twincie/recall"
INSTALL_DIR="${INSTALL_DIR:-/usr/local}"
BIN_DIR="${INSTALL_DIR}/bin"
LIB_DIR="${INSTALL_DIR}/lib/recall"
JAR_NAME="recall-${RECALL_VERSION}.jar"
JAR_PATH="${LIB_DIR}/${JAR_NAME}"

BOLD='\033[1m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { printf "${GREEN}%s${NC}\n" "$*"; }
warn()  { printf "${YELLOW}%s${NC}\n" "$*"; }
header(){ printf "\n${BOLD}${CYAN}%s${NC}\n" "$*"; }

check_java() {
  if ! command -v java &>/dev/null; then
    warn "Java not found. Install Java 17+ from https://adoptium.net"
    exit 1
  fi
  local version
  version=$(java -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')
  if [ "$version" -lt 17 ] 2>/dev/null; then
    warn "Java 17+ required, found version $version"
    exit 1
  fi
  info "Java $version detected"
}

detect_os() {
  case "$(uname -s)" in
    Darwin*)  echo "macos" ;;
    Linux*)   echo "linux" ;;
    MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
    *)        echo "unknown" ;;
  esac
}

download_release() {
  local os=$1
  local url="https://github.com/${REPO}/releases/download/v${RECALL_VERSION}/${JAR_NAME}"

  if [ -f "target/${JAR_NAME}" ]; then
    info "Using local build: target/${JAR_NAME}"
    cp "target/${JAR_NAME}" "$JAR_PATH"
    return
  fi

  info "Downloading recall v${RECALL_VERSION}..."
  if command -v curl &>/dev/null; then
    curl -fsSL "$url" -o "$JAR_PATH"
  elif command -v wget &>/dev/null; then
    wget -q "$url" -O "$JAR_PATH"
  else
    warn "Need curl or wget to download. Build locally: mvn package"
    exit 1
  fi
}

install_wrapper() {
  local wrapper="${BIN_DIR}/recall"
  cat > "$wrapper" << 'WRAPPER'
#!/usr/bin/env bash
JAR_DIR="/usr/local/lib/recall"
JAR_FILE=$(ls -t "${JAR_DIR}"/recall-*.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
  echo "recall JAR not found in ${JAR_DIR}" >&2
  exit 1
fi
exec java -jar "$JAR_FILE" "$@"
WRAPPER
  chmod +x "$wrapper"
  info "Created wrapper: $wrapper"
}

setup_completion() {
  local shell_type
  shell_type=$(basename "$SHELL")
  local rc_file

  case "$shell_type" in
    zsh) rc_file="$HOME/.zshrc" ;;
    bash) rc_file="$HOME/.bashrc" ;;
    *) warn "Unsupported shell for auto-completion: $shell_type"; return ;;
  esac

  if grep -q "recall.*completion" "$rc_file" 2>/dev/null; then
    info "Completion already configured in $rc_file"
    return
  fi

  echo "" >> "$rc_file"
  echo "# recall shell completion" >> "$rc_file"
  if [ "$shell_type" = "zsh" ]; then
    echo "eval \"\$(${BIN_DIR}/recall generate-completion)\"" >> "$rc_file"
  else
    echo "source <(${BIN_DIR}/recall generate-completion)" >> "$rc_file"
  fi
  info "Completion added to $rc_file (restart shell or source it)"
}

main() {
  header "recall v${RECALL_VERSION} installer"
  echo ""

  check_java

  local os
  os=$(detect_os)
  info "Detected OS: $os"
  echo ""

  if [ "$os" = "windows" ]; then
    INSTALL_DIR="/usr/local"
    BIN_DIR="${INSTALL_DIR}/bin"
    LIB_DIR="${INSTALL_DIR}/lib/recall"
  fi

  if [ ! -w "$BIN_DIR" ] || [ ! -w "$INSTALL_DIR" ]; then
    warn "Need sudo to install to ${INSTALL_DIR}"
    if command -v sudo &>/dev/null; then
      sudo mkdir -p "$LIB_DIR" "$BIN_DIR"
      sudo chown "$(whoami)" "$LIB_DIR" "$BIN_DIR" 2>/dev/null || true
    else
      warn "Install manually: sudo mkdir -p ${LIB_DIR} ${BIN_DIR}"
      exit 1
    fi
  fi
  mkdir -p "$LIB_DIR" "$BIN_DIR"

  if [ -f "$JAR_PATH" ]; then
    warn "Removing previous version..."
    rm -f "$JAR_PATH"
  fi

  download_release "$os"
  install_wrapper

  header "Installation complete"
  echo ""
  info "  recall --help"
  echo ""

  if [ -t 0 ]; then
    echo -n "Set up shell completion? [Y/n] "
    read -r resp
    case "$resp" in
      n|N|no|NO) ;;
      *) setup_completion ;;
    esac
  fi
}

main "$@"
