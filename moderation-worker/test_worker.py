"""Unit tests for worker env/contract defaults and classify routing (AUDIT P1-9)."""
import os
import subprocess
import sys

import worker

HERE = os.path.dirname(os.path.abspath(__file__))

# 임포트 시점에 평가되는 모듈 상수를 깨끗한 환경에서 검증하기 위해 별도 인터프리터로 읽는다.
# (같은 프로세스에서 importlib.reload 하면 prometheus_client 메트릭이 중복 등록되어 실패한다.)
_CLEARED_KEYS = [
    "MODERATOR_MODE",
    "METRICS_PORT",
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
    # 계약상 8000 고정 — env로 덮어쓸 수 없어야 한다.
    assert worker_const("METRICS_PORT", {"METRICS_PORT": "9999"}) == 8000


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
