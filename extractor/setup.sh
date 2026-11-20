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

# PPT 변환용 LibreOffice (크기가 커서 자동 설치는 안 함)
if ! uv run python -c "from extractor.stage0_input import find_soffice; import sys; sys.exit(0 if find_soffice() else 1)"; then
  echo ""
  echo "참고: PPT 파일도 분석하려면 LibreOffice 를 설치하세요 (PDF만 쓸 땐 필요 없음)"
  echo "  brew install --cask libreoffice"
fi
