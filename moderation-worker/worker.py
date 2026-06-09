import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

import redis


REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_QUEUE_NAME = os.getenv("REDIS_QUEUE_NAME", "mod:queue")
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")
BLOCK_THRESHOLD = float(os.getenv("MOCK_BLOCK_THRESHOLD", "0.70"))

TOXIC_TERMS = [
    term.strip().lower()
    for term in os.getenv(
        "MOCK_TOXIC_TERMS",
        "바보,멍청,욕설,민폐,불쾌,처참,역겹",
    ).split(",")
    if term.strip()
]


def log(message):
    now = datetime.now(timezone.utc).isoformat()
    print(f"[{now}] {message}", flush=True)


def classify(content):
    normalized = content.lower()
    matched = next((term for term in TOXIC_TERMS if term in normalized), None)
    if matched:
        return {
            "action": "blur",
            "verdict": "BLOCK",
            "score": 0.90,
            "reason": f"mock matched term: {matched}",
        }

    return {
        "action": "pass",
        "verdict": "PASS",
        "score": 0.05,
        "reason": "mock keyword score",
    }


def post_result(job, result):
    payload = {
        "message_id": job["message_id"],
        "action": result["action"],
        "verdict": result["verdict"],
        "score": result["score"],
        "model_version": "mock-worker",
        "reason": result["reason"],
    }
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{BACKEND_BASE_URL}/api/internal/moderation/results",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    with urllib.request.urlopen(request, timeout=5) as response:
        response.read()


def handle_job(raw):
    job = json.loads(raw)
    message_id = job.get("message_id")
    content = job.get("content", "")
    if not message_id:
        raise ValueError("message_id is required")

    result = classify(content)
    log(
        "inspect "
        f"message_id={message_id} "
        f"action={result['action']} "
        f"score={result['score']:.3f} "
        f"reason={result['reason']!r}"
    )
    post_result(job, result)


def main():
    client = redis.Redis(
        host=REDIS_HOST,
        port=REDIS_PORT,
        decode_responses=True,
    )

    log(
        "worker started "
        f"queue={REDIS_QUEUE_NAME} "
        f"backend={BACKEND_BASE_URL} "
        f"toxic_terms={TOXIC_TERMS}"
    )

    while True:
        try:
            item = client.brpop(REDIS_QUEUE_NAME, timeout=5)
            if item is None:
                continue
            _, raw = item
            handle_job(raw)
        except (redis.RedisError, urllib.error.URLError) as exc:
            log(f"temporary error: {exc}")
            time.sleep(2)
        except Exception as exc:
            log(f"job error: {exc}")


if __name__ == "__main__":
    main()
