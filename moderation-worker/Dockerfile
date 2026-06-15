# moderation-worker — Python 3.11 / transformers (in-process, D3)
#
# 핵심 결정 2개:
#  1) torch는 CPU 전용 인덱스로 설치 — PyPI 기본(CUDA 포함) 대비 이미지 ~5GB 절감.
#     requirements.txt의 torch==2.5.1은 CPU 인덱스의 2.5.1+cpu와 매치되므로 수정 불필요.
#  2) AI 모델(kor_unsmile)을 빌드 시 이미지에 굽는다 — KEDA 스케일아웃 시
#     파드마다 HF에서 모델(~400MB)을 다시 받는 콜드스타트 제거(D12 취지).
#     UNSMILE_MODEL_ID를 런타임에 바꾸면 그 모델만 다운로드가 발생한다(기본값은 캐시 적중).
# 런타임 계약(A-5): 메트릭/프로브 포트 8000 (worker.py에 고정).

# ---------- 1단계: 의존성 + 모델 ----------
FROM python:3.11-slim AS build
WORKDIR /workspace

# venv에 설치해 런타임 스테이지로 통째로 복사한다(빌드 캐시·pip 잔재 제외).
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

COPY requirements.txt .
# CPU 인덱스를 우선, 그 외 패키지는 PyPI 폴백.
RUN pip install --no-cache-dir \
    --index-url https://download.pytorch.org/whl/cpu \
    --extra-index-url https://pypi.org/simple \
    -r requirements.txt

# 모델을 이미지에 동결. HF_HOME이 모델 캐시 위치를 결정한다.
ENV HF_HOME=/opt/hf-cache
RUN python -c "from transformers import pipeline; pipeline('text-classification', model='smilegate-ai/kor_unsmile', top_k=None)"

# ---------- 2단계: 런타임 ----------
FROM python:3.11-slim

RUN groupadd --system --gid 999 app && useradd --system --uid 999 --gid 999 --no-create-home app

COPY --from=build /opt/venv /opt/venv
# 캐시 소유권을 비루트 유저로 — transformers가 락파일 등을 쓸 수 있어야 한다.
COPY --from=build --chown=app:app /opt/hf-cache /opt/hf-cache

ENV PATH="/opt/venv/bin:$PATH" \
    HF_HOME=/opt/hf-cache \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY --chown=app:app worker.py .

USER 999
EXPOSE 8000

# exec form: python이 PID 1 — Week4 graceful shutdown(D11, SIGTERM 처리)의 전제.
ENTRYPOINT ["python", "worker.py"]