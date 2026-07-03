"""Unit tests for worker env/contract defaults and classify routing (AUDIT P1-9)."""
import json
import os
import subprocess
import sys

import dlq_replay
import worker

HERE = os.path.dirname(os.path.abspath(__file__))

# 임포트 시점에 평가되는 모듈 상수를 깨끗한 환경에서 검증하기 위해 별도 인터프리터로 읽는다.
# 같은 프로세스에서 reload하지 않고 env 기본값을 독립적으로 확인한다.
_CLEARED_KEYS = [
    "MODERATOR_MODE",
    "METRICS_PORT",
    "REDIS_PASSWORD",
    "MOD_QUEUE_KEY",
    "REDIS_QUEUE_NAME",
    "DB_USER",
    "DB_USERNAME",
]


def worker_const(name, env_overrides=None):
    env = {k: v for k, v in os.environ.items() if k not in _CLEARED_KEYS}
    if env_overrides:
        env.update(env_overrides)
    out = subprocess.check_output(
        [sys.executable, "-c", f"import worker; print(repr(worker.{name}))"],
        cwd=HERE,
        env=env,
    )
    return eval(out.decode().strip())


def test_moderator_mode_defaults_to_real():
    assert worker_const("MODERATOR_MODE") == "real"


def test_explicit_mock_mode_is_honored():
    assert worker_const("MODERATOR_MODE", {"MODERATOR_MODE": "MOCK"}) == "mock"


def test_metrics_port_is_fixed_8000():
    assert worker_const("METRICS_PORT", {"METRICS_PORT": "9999"}) == 8000


def test_redis_password_defaults_to_none():
    assert worker_const("REDIS_PASSWORD") is None


def test_redis_password_reads_contract_key():
    assert worker_const("REDIS_PASSWORD", {"REDIS_PASSWORD": "redis-secret"}) == "redis-secret"


def test_mod_queue_key_ignores_noncontract_alias():
    # 비계약 폴백 키 REDIS_QUEUE_NAME은 더 이상 읽지 않는다.
    assert worker_const("MOD_QUEUE_KEY", {"REDIS_QUEUE_NAME": "legacy:queue"}) == "mod:queue"


def test_db_user_ignores_noncontract_alias():
    # 비계약 폴백 키 DB_USERNAME은 더 이상 읽지 않는다.
    assert worker_const("DB_USER", {"DB_USERNAME": "legacy"}) == "root"


def test_db_user_reads_contract_key():
    assert worker_const("DB_USER", {"DB_USER": "chatguard"}) == "chatguard"


def test_mock_mode_routes_to_mock_classifier(monkeypatch):
    monkeypatch.setattr(worker, "MODERATOR_MODE", "mock")
    result = worker.classify("바보")
    # model_version은 모드와 무관하게 MODEL_VERSION으로 통일(P2-9). 모드 구분은 reason 필드.
    assert result["model_version"] == worker.MODEL_VERSION
    assert "mock" in result["reason"]
    assert result["action"] == "blur"


def test_non_mock_mode_routes_to_real_model(monkeypatch):
    # 기본(real)·unsmile·미설정은 모두 실모델 경로로 가야 한다(mock 경로 아님).
    called = {}

    def fake_real(content):
        called["hit"] = content
        return worker.build_result(0.0, worker.MODEL_VERSION, "stub")

    monkeypatch.setattr(worker, "classify_with_unsmile", fake_real)
    monkeypatch.setattr(worker, "MODERATOR_MODE", "real")
    worker.classify("안녕하세요")

    assert called.get("hit") == "안녕하세요"


def test_dlq_replay_skips_invalid_payload():
    options = dlq_replay.ReplayOptions(
        limit=10,
        scan_limit=10,
        dry_run=True,
        message_id=None,
        allowed_error_keywords=tuple(),
        blocked_error_keywords=tuple(),
        max_replay_count=3,
    )

    eligible, reason = dlq_replay.evaluate_job({"message_id": "m1"}, options)

    assert eligible is False
    assert reason == "invalid_payload"


def test_dlq_replay_dry_run_does_not_move_jobs(monkeypatch):
    raw = json.dumps({"message_id": "m1", "room_id": 1, "last_error": "RedisError"})
    redis_client = FakeRedis({dlq_replay.DLQ_QUEUE_KEY: [raw], dlq_replay.MOD_QUEUE_KEY: []})
    options = dlq_replay.ReplayOptions(
        limit=10,
        scan_limit=10,
        dry_run=True,
        message_id=None,
        allowed_error_keywords=("rediserror",),
        blocked_error_keywords=tuple(),
        max_replay_count=3,
    )

    result = dlq_replay.replay_dlq(redis_client, options)

    assert result == {"scanned": 1, "moved": 1, "skipped": 0}
    assert redis_client.lists[dlq_replay.DLQ_QUEUE_KEY] == [raw]
    assert redis_client.lists[dlq_replay.MOD_QUEUE_KEY] == []


def test_dlq_replay_apply_moves_job_to_main_queue():
    raw = json.dumps({
        "message_id": "m1",
        "room_id": 1,
        "retry_count": 3,
        "last_error": "MySQLError: temporary connection failure",
        "failed_at": "2026-07-03T00:00:00+00:00",
    })
    redis_client = FakeRedis({dlq_replay.DLQ_QUEUE_KEY: [raw], dlq_replay.MOD_QUEUE_KEY: []})
    options = dlq_replay.ReplayOptions(
        limit=10,
        scan_limit=10,
        dry_run=False,
        message_id=None,
        allowed_error_keywords=("mysqlerror",),
        blocked_error_keywords=tuple(),
        max_replay_count=3,
    )

    result = dlq_replay.replay_dlq(redis_client, options)
    replayed = json.loads(redis_client.lists[dlq_replay.MOD_QUEUE_KEY][0])

    assert result == {"scanned": 1, "moved": 1, "skipped": 0}
    assert redis_client.lists[dlq_replay.DLQ_QUEUE_KEY] == []
    assert replayed["message_id"] == "m1"
    assert replayed["room_id"] == 1
    assert replayed["replay_count"] == 1
    assert "retry_count" not in replayed
    assert "last_error" not in replayed
    assert "failed_at" not in replayed


def test_dlq_replay_scan_limit_bounds_redis_lrange():
    jobs = [
        json.dumps({"message_id": f"m{i}", "room_id": 1, "last_error": "RedisError"})
        for i in range(5)
    ]
    redis_client = FakeRedis({dlq_replay.DLQ_QUEUE_KEY: jobs, dlq_replay.MOD_QUEUE_KEY: []})
    options = dlq_replay.ReplayOptions(
        limit=10,
        scan_limit=2,
        dry_run=True,
        message_id=None,
        allowed_error_keywords=("rediserror",),
        blocked_error_keywords=tuple(),
        max_replay_count=3,
    )

    result = dlq_replay.replay_dlq(redis_client, options)

    assert result == {"scanned": 2, "moved": 2, "skipped": 0}
    assert redis_client.lrange_calls == [(dlq_replay.DLQ_QUEUE_KEY, 0, 1)]


class FakeRedis:
    def __init__(self, lists):
        self.lists = {key: list(value) for key, value in lists.items()}
        self.lrange_calls = []

    def lrange(self, key, start, end):
        self.lrange_calls.append((key, start, end))
        values = self.lists.get(key, [])
        if end == -1:
            return values[start:]
        return values[start:end + 1]

    def eval(self, _script, _num_keys, dlq_key, queue_key, raw, payload):
        values = self.lists.setdefault(dlq_key, [])
        try:
            values.remove(raw)
        except ValueError:
            return 0
        self.lists.setdefault(queue_key, []).insert(0, payload)
        return 1
