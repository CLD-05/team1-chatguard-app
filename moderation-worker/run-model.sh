#!/usr/bin/env bash
set -euo pipefail

MODEL_VERSION="unsmile-v1"
BLUR_THRESHOLD="0.40"
CLEAN_PENALTY="0.05"
MODE="real"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model-version)
      MODEL_VERSION="$2"
      shift 2
      ;;
    --blur-threshold)
      BLUR_THRESHOLD="$2"
      shift 2
      ;;
    --clean-penalty)
      CLEAN_PENALTY="$2"
      shift 2
      ;;
    --mode)
      MODE="$2"
      shift 2
      ;;
    -h|--help)
      cat <<'EOF'
Usage: ./run-model.sh [options]

Options:
  --model-version VERSION       Version label stored in moderation_logs
  --blur-threshold NUMBER       Blur threshold
  --clean-penalty NUMBER        Clean-label penalty
  --mode MODE                   Worker mode: real or mock
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

BACKEND_ENV="$SCRIPT_DIR/../backend/.env"
if [[ -f "$BACKEND_ENV" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    [[ -z "$line" || "$line" == \#* || "$line" != *=* ]] && continue
    name="${line%%=*}"
    value="${line#*=}"
    if [[ -z "${!name:-}" ]]; then
      export "$name=$value"
    fi
  done < "$BACKEND_ENV"
fi

PYTHON_BIN=""
for candidate in python3.11 python3 python; do
  if command -v "$candidate" >/dev/null 2>&1; then
    PYTHON_BIN="$candidate"
    break
  fi
done

if [[ -z "$PYTHON_BIN" ]]; then
  echo "Python was not found. Install Python 3.11 first."
  exit 1
fi

if [[ ! -x "$SCRIPT_DIR/.venv/bin/python" ]]; then
  echo "Python virtual environment was not found. Creating .venv..."
  "$PYTHON_BIN" -m venv .venv
fi

VENV_PY="$SCRIPT_DIR/.venv/bin/python"

echo "Installing/updating Python dependencies..."
"$VENV_PY" -m pip install -r requirements.txt

export REDIS_HOST="${REDIS_HOST:-localhost}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export MOD_QUEUE_KEY="${MOD_QUEUE_KEY:-mod:queue}"
export ROOM_CHANNEL_PREFIX="${ROOM_CHANNEL_PREFIX:-room:}"
export DB_URL="${DB_URL:-jdbc:mysql://localhost:3306/chatguard_dev?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}"
export DB_USER="${DB_USER:-root}"
export MODERATOR_MODE="$MODE"
export UNSMILE_MODEL_ID="${UNSMILE_MODEL_ID:-smilegate-ai/kor_unsmile}"
export MODEL_VERSION="$MODEL_VERSION"
export BLUR_THRESHOLD="$BLUR_THRESHOLD"
export CLEAN_PENALTY="$CLEAN_PENALTY"
export METRICS_PORT="${METRICS_PORT:-8000}"
export TOKENIZERS_PARALLELISM="${TOKENIZERS_PARALLELISM:-false}"

echo "Starting ChatGuard moderation worker"
echo "  mode: $MODERATOR_MODE"
echo "  model: $UNSMILE_MODEL_ID"
echo "  model_version: $MODEL_VERSION"
echo "  blur_threshold: $BLUR_THRESHOLD"
echo "  clean_penalty: $CLEAN_PENALTY"
echo "  redis: $REDIS_HOST:$REDIS_PORT / $MOD_QUEUE_KEY"
echo "  db: $DB_URL"
echo "  metrics: http://localhost:$METRICS_PORT/metrics"

exec "$VENV_PY" worker.py
