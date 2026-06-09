# Moderation Worker

Python worker that consumes moderation jobs from Redis and reports moderation
results back to the Spring Boot backend.

This first version is a mock worker. It does not load an AI model yet.

## Setup

```powershell
cd C:\CE\ChatGuard\team1-chatguard-app\moderation-worker
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

## Run

```powershell
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_QUEUE_NAME="mod:queue"
$env:BACKEND_BASE_URL="http://localhost:8080"

python worker.py
```

The worker expects jobs like:

```json
{
  "message_id": "01...",
  "room_id": 1,
  "user_id": 1,
  "content": "message text"
}
```

It sends results to:

```text
POST /api/internal/moderation/results
```
