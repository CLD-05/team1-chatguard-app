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

MODERATOR_MODE = os.getenv("MODERATOR_MODE", "mock").lower()
UNSMILE_MODEL_ID = os.getenv("UNSMILE_MODEL_ID", "smilegate-ai/kor_unsmile")
BLOCK_THRESHOLD = float(os.getenv("BLOCK_THRESHOLD", "0.70"))
DELETE_THRESHOLD = float(os.getenv("DELETE_THRESHOLD", "0.90"))

MOCK_TOXIC_TERMS = [
    term.strip().lower()
    for term in os.getenv(
        "MOCK_TOXIC_TERMS",
        "바보,멍청,욕설,민폐,불쾌,처참,역겹",
    ).split(",")
    if term.strip()
]

TOXIC_LABEL_KEYWORDS = [
    term.strip().lower()
    for term in os.getenv(
        "TOXIC_LABEL_KEYWORDS",
        "악플,욕설,혐오,여성,남성,성소수자,인종,국적,연령,지역,종교,기타",
    ).split(",")
    if term.strip()
]
CLEAN_LABEL_KEYWORDS = [
    term.strip().lower()
    for term in os.getenv("CLEAN_LABEL_KEYWORDS", "clean,정상").split(",")
    if term.strip()
]

_classifier = None


def log(message):
    now = datetime.now(timezone.utc).isoformat()
    print(f"[{now}] {message}", flush=True)


def classify(content):
    if MODERATOR_MODE == "unsmile":
        return classify_with_unsmile(content)
    return classify_with_mock(content)


def classify_with_mock(content):
    normalized = content.lower()
    matched = next((term for term in MOCK_TOXIC_TERMS if term in normalized), None)
    if matched:
        return build_result(0.90, "mock-worker", f"mock matched term: {matched}")
    return build_result(0.05, "mock-worker", "mock keyword score")


def classify_with_unsmile(content):
    global _classifier
    if _classifier is None:
        from transformers import pipeline

        log(f"loading unsmile model={UNSMILE_MODEL_ID}")
        _classifier = pipeline("text-classification", model=UNSMILE_MODEL_ID, top_k=None)
        log("loaded unsmile model")

    raw_result = _classifier(content)
    toxic_score, top_label = extract_toxic_score(raw_result)
    return build_result(toxic_score, "unsmile", f"unsmile:{top_label}={toxic_score:.3f}")


def extract_toxic_score(raw_result):
    rows = raw_result
    if rows and isinstance(rows[0], list):
        rows = rows[0]
    if isinstance(rows, dict):
        rows = [rows]

    best_toxic = 0.0
    best_clean = 0.0
    top_label = "unknown"
    top_score = -1.0

    for row in rows:
        label = str(row.get("label", "unknown"))
        label_lower = label.lower()
        score = float(row.get("score", 0.0))
        if score > top_score:
            top_score = score
            top_label = label
        if any(keyword in label_lower for keyword in TOXIC_LABEL_KEYWORDS):
            best_toxic = max(best_toxic, score)
        if any(keyword in label_lower for keyword in CLEAN_LABEL_KEYWORDS):
            best_clean = max(best_clean, score)

    if best_toxic == 0.0 and best_clean > 0.0:
        return max(0.0, 1.0 - best_clean), top_label
    if best_toxic == 0.0 and rows:
        return max(float(row.get("score", 0.0)) for row in rows), top_label
    return best_toxic, top_label


def build_result(score, model_version, reason):
    if score >= DELETE_THRESHOLD:
        action = "delete"
        verdict = "BLOCK"
    elif score >= BLOCK_THRESHOLD:
        action = "blur"
        verdict = "BLOCK"
    else:
        action = "pass"
        verdict = "PASS"

    return {
        "action": action,
        "verdict": verdict,
        "score": score,
        "model_version": model_version,
        "reason": reason,
    }


def post_result(job, result):
    payload = {
        "message_id": job["message_id"],
        "action": result["action"],
        "verdict": result["verdict"],
        "score": result["score"],
        "model_version": result["model_version"],
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
        f"mode={MODERATOR_MODE} "
        f"queue={REDIS_QUEUE_NAME} "
        f"backend={BACKEND_BASE_URL}"
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
