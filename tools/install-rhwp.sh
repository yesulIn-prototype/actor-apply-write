#!/usr/bin/env bash
# Installs the pinned rhwp release (HWP → PDF renderer, MIT) into tools/rhwp/rhwp/.
# Works on Linux, macOS and Windows (Git Bash). Verifies the published SHA-256 before extracting.
set -euo pipefail

VERSION="v0.8.6"
BASE="https://github.com/edwardkim/rhwp/releases/download/${VERSION}"
DIR="$(cd "$(dirname "$0")" && pwd)/rhwp"

case "$(uname -s)-$(uname -m)" in
  Linux-x86_64)            ASSET="rhwp-${VERSION}-linux-x86_64.tar.gz" ;;
  Linux-aarch64|Linux-arm64) ASSET="rhwp-${VERSION}-linux-aarch64.tar.gz" ;;
  Darwin-arm64)            ASSET="rhwp-${VERSION}-macos-aarch64.tar.gz" ;;
  Darwin-x86_64)           ASSET="rhwp-${VERSION}-macos-x86_64.tar.gz" ;;
  MINGW*|MSYS*|CYGWIN*)    ASSET="rhwp-${VERSION}-windows-x86_64.zip" ;;
  *) echo "지원하지 않는 플랫폼: $(uname -s)-$(uname -m)" >&2; exit 1 ;;
esac

mkdir -p "$DIR"
cd "$DIR"
curl -fsSL -o "$ASSET" "$BASE/$ASSET"
curl -fsSL -o SHA256SUMS.txt "$BASE/SHA256SUMS.txt"
EXPECTED="$(grep " ${ASSET}\$" SHA256SUMS.txt | cut -d' ' -f1)"
ACTUAL="$(sha256sum "$ASSET" 2>/dev/null | cut -d' ' -f1 || shasum -a 256 "$ASSET" | cut -d' ' -f1)"
if [ -z "$EXPECTED" ] || [ "$EXPECTED" != "$ACTUAL" ]; then
  echo "체크섬 불일치: $ASSET" >&2
  exit 1
fi

case "$ASSET" in
  *.zip) unzip -o -q "$ASSET" ;;
  *) tar xzf "$ASSET" ;;
esac
rm -f "$ASSET"
echo "rhwp ${VERSION} 설치 완료: $DIR/rhwp"
