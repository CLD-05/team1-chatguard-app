import json
import os
import time
from datetime import datetime, timezone
from urllib.parse import parse_qs, urlparse

import pymysql
import redis
from prometheus_client import Counter, Histogram, start_http_server


REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
# A-5 env 계약 키만 읽는다(비계약 폴백 REDIS_QUEUE_NAME 제거).
MOD_QUEUE_KEY = os.getenv("MOD_QUEUE_KEY", "mod:queue")
ROOM_CHANNEL_PREFIX = os.getenv("ROOM_CHANNEL_PREFIX", "room:")

DB_URL = os.getenv("DB_URL", "jdbc:mysql://localhost:3306/chatguard_dev?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true")
# A-5 env 계약 키만 읽는다(비계약 폴백 DB_USERNAME 제거).
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")

# A-1 Worker 책임 = 모델 in-process 판정. 기본값은 real(실모델), mock은 명시 설정 시에만.
MODERATOR_MODE = os.getenv("MODERATOR_MODE", "real").lower()
UNSMILE_MODEL_ID = os.getenv("UNSMILE_MODEL_ID", "smilegate-ai/kor_unsmile")
MODEL_VERSION = os.getenv("MODEL_VERSION", "unsmile-v1")
BLUR_THRESHOLD = float(os.getenv("BLUR_THRESHOLD", "0.40"))
CLEAN_PENALTY = float(os.getenv("CLEAN_PENALTY", "0.05"))
# A-5 런타임 계약: Worker 메트릭 포트는 8000 고정.
METRICS_PORT = 8000

MOCK_TOXIC_TERMS = [
    term.strip().lower()
    for term in os.getenv(
        "MOCK_TOXIC_TERMS",
        "바보,멍청,욕설,민폐,불쾌,처참,역겹",
    ).split(",")
    if term.strip()
]

TOXIC_LABELS = [
    "여성/가족",
    "남성",
    "성소수자",
    "인종/국적",
    "연령",
    "지역",
    "종교",
    "기타 혐오",
    "악플/욕설",
]
WEIGHTS = {
    "여성/가족": 1.15,
    "남성": 1.0,
    "성소수자": 1.25,
    "인종/국적": 1.25,
    "연령": 1.05,
    "지역": 1.05,
    "종교": 1.15,
    "기타 혐오": 1.10,
    "악플/욕설": 1.0,
}
_classifier = None

JOBS_TOTAL = Counter(
    "moderation_jobs_total",
    "Total moderation jobs processed by verdict.",
    ["verdict"],
)
INFERENCE_SECONDS = Histogram(
    "moderation_inference_seconds",
    "Time spent running the moderation model.",
)
QUEUE_WAIT_SECONDS = Histogram(
    "moderation_queue_wait_seconds",
    "Time between queue enqueue and worker dequeue.",
)
E2E_SECONDS = Histogram(
    "moderation_e2e_seconds",
    "Time between queue enqueue and moderation.hide publish for blocked jobs.",
)


def log(message):
    now = datetime.now(timezone.utc).isoformat()
    print(f"[{now}] {message}", flush=True)


def classify(content):
    start = time.perf_counter()
    try:
        # mock은 명시 설정 시에만. 그 외(real/unsmile/미설정)는 실모델 판정.
        if MODERATOR_MODE == "mock":
            return classify_with_mock(content)
        return classify_with_unsmile(content)
    finally:
        INFERENCE_SECONDS.observe(time.perf_counter() - start)


def classify_with_mock(content):
    normalized = content.lower()
    matched = next((term for term in MOCK_TOXIC_TERMS if term in normalized), None)
    # model_version 표기는 모드와 무관하게 MODEL_VERSION으로 통일한다(A-2). 모드 구분은 reason 필드가 한다.
    if matched:
        return build_result(0.90, MODEL_VERSION, f"mock matched term: {matched}")
    return build_result(0.05, MODEL_VERSION, "mock keyword score")


def classify_with_unsmile(content):
    global _classifier
    if _classifier is None:
        from transformers import pipeline

        log(f"loading unsmile model={UNSMILE_MODEL_ID}")
        _classifier = pipeline("text-classification", model=UNSMILE_MODEL_ID, top_k=None)
        log("loaded unsmile model")

    raw_result = _classifier(content)
    final_score, top_label = extract_toxic_score(raw_result)
    return build_result(
        final_score,
        MODEL_VERSION,
        f"unsmile_weighted:{top_label}={final_score:.3f}",
    )


def extract_toxic_score(raw_result):
    rows = raw_result
    if rows and isinstance(rows[0], list):
        rows = rows[0]
    if isinstance(rows, dict):
        rows = [rows]

    scores = {label: 0.0 for label in TOXIC_LABELS}
    scores["clean"] = 0.0

    for row in rows:
        label = str(row.get("label", "unknown"))
        score = float(row.get("score", 0.0))
        if label in scores:
            scores[label] = score
        elif label.lower() == "clean":
            scores["clean"] = score

    top_label = max(
        TOXIC_LABELS,
        key=lambda label: scores[label] * WEIGHTS[label],
    )
    raw_toxic_score = scores[top_label]
    weighted_toxic_score = raw_toxic_score * WEIGHTS[top_label]
    clean_score = scores["clean"]
    final_score = max(0.0, min(1.0, weighted_toxic_score - clean_score * CLEAN_PENALTY))

    return final_score, top_label


def build_result(score, model_version, reason):
    if score >= BLUR_THRESHOLD:
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


def handle_job(raw, redis_client):
    dequeued_at = time.time()
    job = json.loads(raw)
    message_id = job.get("message_id")
    room_id = job.get("room_id")
    content = job.get("content", "")
    if not message_id:
        raise ValueError("message_id is required")
    if room_id is None:
        raise ValueError("room_id is required")

    enqueued_at = parse_enqueued_at(job.get("enqueued_at"))
    if enqueued_at is not None:
        QUEUE_WAIT_SECONDS.observe(max(0.0, dequeued_at - enqueued_at))

    result = classify(content)
    log(
        "inspect "
        f"message_id={message_id} "
        f"action={result['action']} "
        f"score={result['score']:.3f} "
        f"reason={result['reason']!r}"
    )

    apply_result_to_db(job, result)
    JOBS_TOTAL.labels(verdict=result["verdict"].lower()).inc()

    if result["action"] == "blur":
        publish_hide(redis_client, room_id, message_id, result["action"])
        if enqueued_at is not None:
            E2E_SECONDS.observe(max(0.0, time.time() - enqueued_at))


def apply_result_to_db(job, result):
    message_id = job["message_id"]
    checked_at = datetime.now(timezone.utc).replace(tzinfo=None)
    connection = pymysql.connect(
        **parse_db_url(DB_URL),
        user=DB_USER,
        password=DB_PASSWORD,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with connection.cursor() as cursor:
            if result["action"] == "blur":
                cursor.execute(
                    "UPDATE messages SET status = %s WHERE id = %s",
                    ("BLURRED", message_id),
                )
                if cursor.rowcount == 0:
                    raise ValueError(f"message not found: {message_id}")

            cursor.execute(
                """
                INSERT INTO moderation_logs
                    (message_id, stage, verdict, score, model_version, reason, content, checked_at)
                VALUES
                    (%s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    message_id,
                    "AI",
                    result["verdict"],
                    float(result["score"]),
                    result["model_version"],
                    result["reason"],
                    job.get("content") if result["verdict"] == "BLOCK" else None,
                    checked_at,
                ),
            )
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def publish_hide(redis_client, room_id, message_id, action):
    payload = {
        "type": "moderation.hide",
        "payload": {
            "id": message_id,
            "action": action,
        },
    }
    redis_client.publish(f"{ROOM_CHANNEL_PREFIX}{room_id}", json.dumps(payload, ensure_ascii=False))


def parse_db_url(db_url):
    normalized = db_url
    if normalized.startswith("jdbc:"):
        normalized = normalized[len("jdbc:"):]
    parsed = urlparse(normalized)
    query = parse_qs(parsed.query)
    return {
        "host": parsed.hostname or "localhost",
        "port": parsed.port or 3306,
        "database": parsed.path.lstrip("/") or "chatguard_dev",
        "connect_timeout": int(first(query, "connectTimeout", "5")),
        "read_timeout": int(first(query, "socketTimeout", "5")),
        "write_timeout": int(first(query, "socketTimeout", "5")),
    }


def first(query, key, default):
    values = query.get(key)
    return values[0] if values else default


def parse_enqueued_at(value):
    if not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
    except ValueError:
        log(f"invalid enqueued_at={value!r}")
        return None


def requeue_job(redis_client, raw):
    redis_client.lpush(MOD_QUEUE_KEY, raw)
    log("requeued moderation job after temporary failure")


def main():
    start_http_server(METRICS_PORT)
    redis_client = redis.Redis(
        host=REDIS_HOST,
        port=REDIS_PORT,
        decode_responses=True,
    )

    log(
        "worker started "
        f"mode={MODERATOR_MODE} "
        f"model={UNSMILE_MODEL_ID} "
        f"model_version={MODEL_VERSION} "
        f"blur_threshold={BLUR_THRESHOLD:.2f} "
        f"clean_penalty={CLEAN_PENALTY:.2f} "
        f"queue={MOD_QUEUE_KEY} "
        f"redis={REDIS_HOST}:{REDIS_PORT} "
        f"db={DB_URL} "
        f"metrics_port={METRICS_PORT}"
    )

    while True:
        raw = None
        try:
            item = redis_client.brpop(MOD_QUEUE_KEY, timeout=5)
            if item is None:
                continue
            _, raw = item
            handle_job(raw, redis_client)
        except (redis.RedisError, pymysql.MySQLError, TimeoutError, OSError) as exc:
            if raw is not None:
                requeue_job(redis_client, raw)
            log(f"temporary error: {exc}")
            time.sleep(2)
        except Exception as exc:
            log(f"job error: {exc}")


if __name__ == "__main__":
    main()
