import argparse
import json
import os
from dataclasses import dataclass
from datetime import datetime, timezone


REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD") or None
MOD_QUEUE_KEY = os.getenv("MOD_QUEUE_KEY", "mod:queue")
DLQ_QUEUE_KEY = os.getenv("DLQ_QUEUE_KEY", f"{MOD_QUEUE_KEY}:dlq")

DEFAULT_BLOCKED_ERROR_KEYWORDS = (
    "message_id is required,"
    "room_id is required,"
    "message not found,"
    "jsondecodeerror,"
    "expecting value"
)

MOVE_TO_QUEUE_SCRIPT = """
local removed = redis.call('LREM', KEYS[1], 1, ARGV[1])
if removed > 0 then
  redis.call('LPUSH', KEYS[2], ARGV[2])
end
return removed
"""


@dataclass(frozen=True)
class ReplayOptions:
    limit: int
    scan_limit: int
    dry_run: bool
    message_id: str | None
    allowed_error_keywords: tuple[str, ...]
    blocked_error_keywords: tuple[str, ...]
    max_replay_count: int


def log(message):
    now = datetime.now(timezone.utc).isoformat()
    print(f"[{now}] {message}", flush=True)


def parse_bool(value):
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}


def parse_keywords(value):
    if not value:
        return tuple()
    return tuple(item.strip().lower() for item in value.split(",") if item.strip())


def parse_args():
    parser = argparse.ArgumentParser(
        description="Replay safe moderation DLQ jobs back to the main moderation queue."
    )
    default_limit = int(os.getenv("DLQ_REPLAY_LIMIT", "20"))
    parser.add_argument("--limit", type=int, default=default_limit)
    parser.add_argument(
        "--scan-limit",
        type=int,
        default=int(os.getenv("DLQ_REPLAY_SCAN_LIMIT", str(max(default_limit * 5, default_limit)))),
        help="Maximum DLQ entries to scan in one run.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        default=parse_bool(os.getenv("DLQ_REPLAY_APPLY", "false")),
        help="Move eligible jobs. Default is dry-run.",
    )
    parser.add_argument(
        "--message-id",
        default=os.getenv("DLQ_REPLAY_MESSAGE_ID") or None,
        help="Replay only one message_id.",
    )
    parser.add_argument(
        "--allowed-errors",
        default=os.getenv("DLQ_REPLAY_ALLOWED_ERRORS", ""),
        help="Comma-separated last_error substrings allowed for replay. Empty means no allow-list.",
    )
    parser.add_argument(
        "--blocked-errors",
        default=os.getenv("DLQ_REPLAY_BLOCKED_ERRORS", DEFAULT_BLOCKED_ERROR_KEYWORDS),
        help="Comma-separated last_error substrings that must never be replayed.",
    )
    parser.add_argument(
        "--max-replay-count",
        type=int,
        default=int(os.getenv("DLQ_REPLAY_MAX_REPLAY_COUNT", "3")),
        help="Skip jobs whose replay_count is already this value or higher.",
    )
    args = parser.parse_args()
    return ReplayOptions(
        limit=max(0, args.limit),
        scan_limit=max(0, args.scan_limit),
        dry_run=not args.apply,
        message_id=args.message_id,
        allowed_error_keywords=parse_keywords(args.allowed_errors),
        blocked_error_keywords=parse_keywords(args.blocked_errors),
        max_replay_count=max(1, args.max_replay_count),
    )


def decode_job(raw):
    try:
        job = json.loads(raw)
        if isinstance(job, dict):
            return job
    except json.JSONDecodeError:
        pass
    return {"raw": raw}


def cleanup_for_replay(job):
    replay_job = dict(job)
    for key in ("retry_count", "last_error", "failed_at", "processing_started_at", "worker_id"):
        replay_job.pop(key, None)
    replayed_at = datetime.now(timezone.utc).isoformat()
    if replay_job.get("enqueued_at") and not replay_job.get("original_enqueued_at"):
        replay_job["original_enqueued_at"] = replay_job["enqueued_at"]
    replay_job["enqueued_at"] = replayed_at
    replay_job["replayed_at"] = replayed_at
    replay_job["replay_count"] = int(job.get("replay_count", 0) or 0) + 1
    return replay_job


def is_structurally_valid(job):
    return bool(job.get("message_id")) and job.get("room_id") is not None


def matches_any(value, keywords):
    lowered = str(value or "").lower()
    return any(keyword in lowered for keyword in keywords)


def evaluate_job(job, options):
    message_id = job.get("message_id")
    last_error = job.get("last_error", "")

    if options.message_id and message_id != options.message_id:
        return False, "message_id_filter"
    if not is_structurally_valid(job):
        return False, "invalid_payload"
    if int(job.get("replay_count", 0) or 0) >= options.max_replay_count:
        return False, "max_replay_count"
    if options.blocked_error_keywords and matches_any(last_error, options.blocked_error_keywords):
        return False, "blocked_error"
    if options.allowed_error_keywords and not matches_any(last_error, options.allowed_error_keywords):
        return False, "not_allowed_error"
    return True, "eligible"


def replay_dlq(redis_client, options):
    scanned = moved = skipped = 0
    if options.limit == 0 or options.scan_limit == 0:
        log(
            "dlq replay finished "
            f"dry_run={options.dry_run} "
            "scanned=0 "
            "selected_or_moved=0 "
            "skipped=0 "
            f"dlq={DLQ_QUEUE_KEY} "
            f"queue={MOD_QUEUE_KEY}"
        )
        return {"scanned": 0, "moved": 0, "skipped": 0}

    for raw in redis_client.lrange(DLQ_QUEUE_KEY, 0, options.scan_limit - 1):
        if moved >= options.limit:
            break
        scanned += 1
        job = decode_job(raw)
        eligible, reason = evaluate_job(job, options)
        message_id = job.get("message_id", "unknown")
        if not eligible:
            skipped += 1
            log(f"skip dlq job message_id={message_id} reason={reason}")
            continue

        replay_payload = json.dumps(cleanup_for_replay(job), ensure_ascii=False)
        if options.dry_run:
            moved += 1
            log(f"dry-run replay candidate message_id={message_id}")
            continue

        removed = redis_client.eval(MOVE_TO_QUEUE_SCRIPT, 2, DLQ_QUEUE_KEY, MOD_QUEUE_KEY, raw, replay_payload)
        if int(removed or 0) > 0:
            moved += 1
            log(f"replayed dlq job message_id={message_id} queue={MOD_QUEUE_KEY}")
        else:
            skipped += 1
            log(f"skip dlq job message_id={message_id} reason=not_found")

    log(
        "dlq replay finished "
        f"dry_run={options.dry_run} "
        f"scanned={scanned} "
        f"selected_or_moved={moved} "
        f"skipped={skipped} "
        f"dlq={DLQ_QUEUE_KEY} "
        f"queue={MOD_QUEUE_KEY}"
    )
    return {"scanned": scanned, "moved": moved, "skipped": skipped}


def main():
    import redis

    options = parse_args()
    redis_client = redis.Redis(
        host=REDIS_HOST,
        port=REDIS_PORT,
        password=REDIS_PASSWORD,
        decode_responses=True,
    )
    replay_dlq(redis_client, options)


if __name__ == "__main__":
    main()
