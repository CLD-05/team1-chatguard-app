#!/usr/bin/env bash
# macOS / Linux용 실행 스크립트 (Windows의 run-unsmile.ps1 대응판).
# .venv 생성/패키지 설치/환경변수 기본값 설정 후 worker를 실행한다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# backend/.env에서 DB_PASSWORD 등 로드 (이미 설정된 값은 덮어쓰지 않는다)
BACKEND_ENV="$SCRIPT_DIR/../backend/.env"
if [[ -f "$BACKEND_ENV" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"   # 앞 공백 제거
    [[ -z "$line" || "$line" == \#* || "$line" != *=* ]] && continue
    name="${line%%=*}"
    value="${line#*=}"
    if [[ -z "${!name:-}" ]]; then
      export "$name=$value"
    fi
  done < "$BACKEND_ENV"
fi

# Python 3.11 우선 선택 (torch==2.5.1은 3.14 wheel이 없다)
PYTHON_BIN=""
for candidate in python3.11 python3; do
  if command -v "$candidate" >/dev/null 2>&1; then
    PYTHON_BIN="$candidate"
    break
  fi
done
if [[ -z "$PYTHON_BIN" ]]; then
  echo "Python을 찾을 수 없습니다. Python 3.11을 먼저 설치하세요 (brew install python@3.11)."
  exit 1
fi

if [[ ! -x "$SCRIPT_DIR/.venv/bin/python" ]]; then
  echo "가상환경(.venv)이 없어 새로 생성합니다... ($PYTHON_BIN)"
  "$PYTHON_BIN" -m venv .venv
fi

VENV_PY="$SCRIPT_DIR/.venv/bin/python"

echo "Python 의존성 설치/업데이트 중..."
"$VENV_PY" -m pip install --upgrade pip >/dev/null
"$VENV_PY" -m pip install -r requirements.txt

# 환경변수 기본값 (이미 지정된 값은 유지)
export REDIS_HOST="${REDIS_HOST:-localhost}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export MOD_QUEUE_KEY="${MOD_QUEUE_KEY:-mod:queue}"
export ROOM_CHANNEL_PREFIX="${ROOM_CHANNEL_PREFIX:-room:}"
export DB_URL="${DB_URL:-jdbc:mysql://localhost:3306/chatguard_dev?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}"
export DB_USER="${DB_USER:-root}"
export MODERATOR_MODE="unsmile"
export UNSMILE_MODEL_ID="${UNSMILE_MODEL_ID:-smilegate-ai/kor_unsmile}"
export MODEL_VERSION="${MODEL_VERSION:-unsmile-v1}"
export BLOCK_THRESHOLD="${BLOCK_THRESHOLD:-0.70}"
# tokenizers fork 경고 억제
export TOKENIZERS_PARALLELISM="${TOKENIZERS_PARALLELISM:-false}"

echo "ChatGuard moderation worker 시작"
echo "  mode: $MODERATOR_MODE"
echo "  model: $UNSMILE_MODEL_ID"
echo "  model_version: $MODEL_VERSION"
echo "  redis: $REDIS_HOST:$REDIS_PORT / $MOD_QUEUE_KEY"
echo "  db: $DB_URL"
echo "  metrics: http://localhost:8000/metrics"

exec "$VENV_PY" worker.py
