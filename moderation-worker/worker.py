import json
import os
import signal
import socket
import time
from datetime import datetime, timezone
from urllib.parse import parse_qs, urlparse

import pymysql
import redis
import torch
from dbutils.pooled_db import PooledDB
from prometheus_client import Counter, Histogram, start_http_server


REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
# A-5 env 계약 키만 읽는다(비계약 폴백 REDIS_QUEUE_NAME 제거).
MOD_QUEUE_KEY = os.getenv("MOD_QUEUE_KEY", "mod:queue")
PROCESSING_QUEUE_KEY = os.getenv("PROCESSING_QUEUE_KEY", f"{MOD_QUEUE_KEY}:processing")
DLQ_QUEUE_KEY = os.getenv("DLQ_QUEUE_KEY", f"{MOD_QUEUE_KEY}:dlq")
MAX_RETRY_COUNT = int(os.getenv("MAX_RETRY_COUNT", "3"))
PROCESSING_TIMEOUT_SECONDS = int(os.getenv("PROCESSING_TIMEOUT_SECONDS", "300"))
RECOVER_PROCESSING_ON_STARTUP = os.getenv("RECOVER_PROCESSING_ON_STARTUP", "true").lower() == "true"
WORKER_ID = os.getenv("WORKER_ID", socket.gethostname())
ROOM_CHANNEL_PREFIX = os.getenv("ROOM_CHANNEL_PREFIX", "room:")

DB_URL = os.getenv("DB_URL", "jdbc:mysql://localhost:3306/chatguard_dev?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true")
# A-5 env 계약 키만 읽는다(비계약 폴백 DB_USERNAME 제거).
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DB_POOL_MAX_CONNECTIONS = int(os.getenv("DB_POOL_MAX_CONNECTIONS", "5"))

# A-1 Worker 책임 = 모델 in-process 판정. 기본값은 real(실모델), mock은 명시 설정 시에만.
MODERATOR_MODE = os.getenv("MODERATOR_MODE", "real").lower()
UNSMILE_MODEL_ID = os.getenv("UNSMILE_MODEL_ID", "smilegate-ai/kor_unsmile")
MODEL_VERSION = os.getenv("MODEL_VERSION", "unsmile-weighted-v1")
BLUR_THRESHOLD = float(os.getenv("BLUR_THRESHOLD", "0.40"))
CLEAN_PENALTY = float(os.getenv("CLEAN_PENALTY", "0.10"))
UNSMILE_WARMUP_ENABLED = os.getenv("UNSMILE_WARMUP_ENABLED", "true").lower() == "true"
UNSMILE_WARMUP_TEXT = os.getenv("UNSMILE_WARMUP_TEXT", "warmup message")
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
_db_pool = None
_shutdown_requested = False

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
RETRIES_TOTAL = Counter(
    "moderation_retries_total",
    "Total moderation jobs requeued after temporary failures.",
)
DLQ_TOTAL = Counter(
    "moderation_dlq_total",
    "Total moderation jobs moved to dead letter queue.",
)
RECOVERED_TOTAL = Counter(
    "moderation_recovered_processing_total",
    "Total stale processing jobs recovered to the main queue.",
)


def log(message):
    now = datetime.now(timezone.utc).isoformat()
    print(f"[{now}] {message}", flush=True)


def request_shutdown(signum, _frame):
    global _shutdown_requested
    if not _shutdown_requested:
        log(f"shutdown requested signal={signum}; will stop after current job")
    _shutdown_requested = True


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

    with torch.inference_mode():
        raw_result = _classifier(content)
    final_score, top_label = extract_toxic_score(raw_result)
    return build_result(
        final_score,
        MODEL_VERSION,
        f"unsmile_weighted:{top_label}={final_score:.3f}",
    )


def warm_up_unsmile_model():
    if MODERATOR_MODE == "mock" or not UNSMILE_WARMUP_ENABLED:
        return

    log("warming up unsmile model")
    classify_with_unsmile(UNSMILE_WARMUP_TEXT)
    log("warmed up unsmile model")


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
    connection = get_db_connection()
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


def get_db_connection():
    return get_db_pool().connection()


def get_db_pool():
    global _db_pool
    if _db_pool is None:
        _db_pool = PooledDB(
            creator=pymysql,
            maxconnections=DB_POOL_MAX_CONNECTIONS,
            mincached=1,
            maxcached=DB_POOL_MAX_CONNECTIONS,
            blocking=True,
            ping=1,
            **parse_db_url(DB_URL),
            user=DB_USER,
            password=DB_PASSWORD,
            charset="utf8mb4",
            autocommit=False,
        )
    return _db_pool


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


def claim_processing_job(redis_client, raw):
    job = decode_job_for_queue(raw)
    job["processing_started_at"] = datetime.now(timezone.utc).isoformat()
    job["worker_id"] = WORKER_ID
    claimed_raw = json.dumps(job, ensure_ascii=False)

    pipe = redis_client.pipeline()
    pipe.lpush(PROCESSING_QUEUE_KEY, claimed_raw)
    pipe.lrem(PROCESSING_QUEUE_KEY, 1, raw)
    _, removed = pipe.execute()
    if removed == 0:
        log("processing claim left original job because it was not found")
    return claimed_raw


def decode_job_for_queue(raw):
    try:
        job = json.loads(raw)
        if isinstance(job, dict):
            return job
    except json.JSONDecodeError:
        pass
    return {"raw": raw}


def cleanup_processing_fields(job):
    job.pop("processing_started_at", None)
    job.pop("worker_id", None)
    return job


def recover_stale_processing_jobs(redis_client):
    if not RECOVER_PROCESSING_ON_STARTUP:
        return

    now = time.time()
    recovered = 0
    for raw in redis_client.lrange(PROCESSING_QUEUE_KEY, 0, -1):
        job = decode_job_for_queue(raw)
        started_at = parse_enqueued_at(job.get("processing_started_at"))
        if started_at is not None and now - started_at < PROCESSING_TIMEOUT_SECONDS:
            continue

        payload = json.dumps(cleanup_processing_fields(job), ensure_ascii=False)
        removed = redis_client.lrem(PROCESSING_QUEUE_KEY, 1, raw)
        if removed:
            redis_client.lpush(MOD_QUEUE_KEY, payload)
            recovered += 1

    if recovered:
        RECOVERED_TOTAL.inc(recovered)
        log(f"recovered stale processing jobs count={recovered}")


def ack_processing_job(redis_client, raw):
    removed = redis_client.lrem(PROCESSING_QUEUE_KEY, 1, raw)
    if removed == 0:
        log("processing ack skipped because job was not found")


def build_failed_job(raw, exc):
    job = cleanup_processing_fields(decode_job_for_queue(raw))

    try:
        retry_count = int(job.get("retry_count", 0)) + 1
    except (TypeError, ValueError):
        retry_count = 1

    job["retry_count"] = retry_count
    job["last_error"] = str(exc)
    job["failed_at"] = datetime.now(timezone.utc).isoformat()
    return job, retry_count


def retry_or_dlq_job(redis_client, raw, exc):
    failed_job, retry_count = build_failed_job(raw, exc)
    payload = json.dumps(failed_job, ensure_ascii=False)
    message_id = failed_job.get("message_id", "unknown")

    if retry_count >= MAX_RETRY_COUNT:
        redis_client.lpush(DLQ_QUEUE_KEY, payload)
        ack_processing_job(redis_client, raw)
        DLQ_TOTAL.inc()
        log(
            "moved moderation job to dlq "
            f"message_id={message_id} "
            f"retry_count={retry_count} "
            f"dlq={DLQ_QUEUE_KEY}"
        )
        return

    redis_client.lpush(MOD_QUEUE_KEY, payload)
    ack_processing_job(redis_client, raw)
    RETRIES_TOTAL.inc()
    log(
        "requeued moderation job after failure "
        f"message_id={message_id} "
        f"retry_count={retry_count}/{MAX_RETRY_COUNT}"
    )


def main():
    signal.signal(signal.SIGTERM, request_shutdown)
    signal.signal(signal.SIGINT, request_shutdown)

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
        f"warmup_enabled={UNSMILE_WARMUP_ENABLED} "
        f"db_pool_max_connections={DB_POOL_MAX_CONNECTIONS} "
        f"queue={MOD_QUEUE_KEY} "
        f"processing_queue={PROCESSING_QUEUE_KEY} "
        f"dlq={DLQ_QUEUE_KEY} "
        f"max_retry_count={MAX_RETRY_COUNT} "
        f"processing_timeout_seconds={PROCESSING_TIMEOUT_SECONDS} "
        f"recover_processing_on_startup={RECOVER_PROCESSING_ON_STARTUP} "
        f"worker_id={WORKER_ID} "
        f"redis={REDIS_HOST}:{REDIS_PORT} "
        f"db={DB_URL} "
        f"metrics_port={METRICS_PORT}"
    )

    try:
        warm_up_unsmile_model()
    except Exception as exc:
        log(f"warm-up failed (non-fatal): {exc}")

    recover_stale_processing_jobs(redis_client)

    while not _shutdown_requested:
        raw = None
        try:
            raw = redis_client.brpoplpush(MOD_QUEUE_KEY, PROCESSING_QUEUE_KEY, timeout=5)
            if raw is None:
                continue
            raw = claim_processing_job(redis_client, raw)
            handle_job(raw, redis_client)
            ack_processing_job(redis_client, raw)
        except (redis.RedisError, pymysql.MySQLError, TimeoutError, OSError) as exc:
            if raw is not None:
                retry_or_dlq_job(redis_client, raw, exc)
            log(f"temporary error: {exc}")
            if _shutdown_requested:
                break
            time.sleep(2)
        except Exception as exc:
            if raw is not None:
                retry_or_dlq_job(redis_client, raw, exc)
            log(f"job error: {exc}")

    log("worker stopped")


if __name__ == "__main__":
    main()
