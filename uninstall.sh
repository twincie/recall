#!/usr/bin/env bash
set -euo pipefail

BOLD='\033[1m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

info()  { printf "${GREEN}%s${NC}\n" "$*"; }
warn()  { printf "${YELLOW}%s${NC}\n" "$*"; }
header(){ printf "\n${BOLD}${CYAN}%s${NC}\n" "$*"; }

INSTALL_DIR="${INSTALL_DIR:-/usr/local}"
BIN_DIR="${INSTALL_DIR}/bin"
LIB_DIR="${INSTALL_DIR}/lib/recall"
DATA_DIR="${HOME}/.recall"

echo ""
header "recall uninstaller"
echo ""

# Remove wrapper
if [ -f "${BIN_DIR}/recall" ]; then
  if [ ! -w "${BIN_DIR}" ]; then
    sudo rm -f "${BIN_DIR}/recall"
  else
    rm -f "${BIN_DIR}/recall"
  fi
  info "Removed wrapper: ${BIN_DIR}/recall"
fi

# Remove JAR
if [ -d "$LIB_DIR" ]; then
  if [ ! -w "$LIB_DIR" ]; then
    sudo rm -rf "$LIB_DIR"
  else
    rm -rf "$LIB_DIR"
  fi
  info "Removed: ${LIB_DIR}"
fi

# Optionally remove data
if [ -d "$DATA_DIR" ]; then
  echo ""
  warn "Your data is at ${DATA_DIR}"
  read -r -p "Remove all recall data? [y/N] " resp
  case "$resp" in
    y|Y|yes|YES)
      rm -rf "$DATA_DIR"
      info "Removed: ${DATA_DIR}"
      ;;
    *)
      info "Data kept at: ${DATA_DIR}"
      ;;
  esac
fi

# Clean shell completion
for rc in "$HOME/.zshrc" "$HOME/.bashrc"; do
  if [ -f "$rc" ]; then
    if grep -q "recall.*completion" "$rc" 2>/dev/null; then
      sed -i.bak '/recall.*completion/d' "$rc" && rm -f "$rc.bak"
      info "Cleaned completion from: $rc"
    fi
  fi
done

# Remove Homebrew if installed that way
if command -v brew &>/dev/null && brew list recall &>/dev/null 2>&1; then
  echo ""
  info "Detected Homebrew installation"
  read -r -p "Uninstall via Homebrew? [Y/n] " resp
  case "$resp" in
    n|N|no|NO) ;;
    *)
      brew uninstall recall
      info "Homebrew formula removed"
      ;;
  esac
fi

echo ""
header "Done"
