#!/usr/bin/env bash
# macOS / Linux 설치: ./setup.sh
set -e
cd "$(dirname "$0")"

if ! command -v uv >/dev/null 2>&1; then
  echo "[1/3] uv 설치"
  curl -LsSf https://astral.sh/uv/install.sh | sh
  export PATH="$HOME/.local/bin:$PATH"
fi

echo "[2/3] 파이썬 3.11 + 패키지 설치"
uv sync

echo "[3/3] 환경 점검 (첫 실행은 OCR 모델 다운로드)"
uv run python -m extractor.check_env
