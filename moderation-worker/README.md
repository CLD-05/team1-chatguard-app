# ChatGuard Moderation Worker

Python 기반 AI 검열 Worker입니다. Redis 큐에서 채팅 메시지를 가져와 UnSmile 모델로 검열하고, 결과를 MySQL과 Redis pub/sub에 반영합니다.

## 한눈에 보기

```text
프론트엔드 채팅 전송
-> 백엔드가 messages 테이블에 메시지 저장
-> 백엔드가 Redis mod:queue에 검열 작업 등록
-> worker가 Redis 큐에서 작업 조회
-> UnSmile 모델로 메시지 검열
-> worker가 moderation_logs에 AI 검열 로그 저장
-> BLOCK이면 messages.status를 BLURRED로 변경
-> BLOCK이면 Redis room:{room_id}에 moderation.hide publish
-> 백엔드가 Redis pub/sub 메시지를 WebSocket 클라이언트에 전달
```

현재 v1에서는 AI 검열 결과로 `delete`를 발행하지 않고 `blur`만 발행합니다.

## 사전 준비

필요한 항목:

- Python 3.11 권장
- Docker / Docker Compose
- 백엔드의 MySQL, Redis 컨테이너
- `backend/.env`

`torch==2.5.1`은 Python 3.12 이상 wheel 지원이 제한적이고, Python 3.14 wheel은 없습니다. 로컬 개발은 Python 3.11 사용을 권장합니다.

## 1. 백엔드 인프라 실행

worker는 백엔드의 MySQL과 Redis를 사용합니다. 먼저 `backend/.env`를 만들고 컨테이너를 실행합니다.

Windows PowerShell:

```powershell
cd backend
copy .env.example .env
# .env 파일에서 DB_PASSWORD 값을 설정
docker compose up -d
```

macOS / Linux:

```bash
cd backend
cp .env.example .env
# .env 파일에서 DB_PASSWORD 값을 설정
docker compose up -d
```

`backend/.env`는 커밋하지 않습니다. worker 실행 스크립트는 기본적으로 `backend/.env`의 `DB_PASSWORD` 값을 읽어 MySQL에 접속합니다.

## 2. Worker 실행

`moderation-worker` 디렉터리에서 OS에 맞는 실행 스크립트 하나만 실행합니다.

Windows PowerShell:

```powershell
cd moderation-worker
.\run-model.ps1
```

macOS / Linux:

```bash
cd moderation-worker
./run-model.sh
```

실행 스크립트가 자동으로 처리하는 일:

- `.venv`가 없으면 생성
- `requirements.txt` 패키지 설치/업데이트
- `backend/.env`에서 `DB_PASSWORD` 로드
- Redis, MySQL, 모델 관련 환경변수 기본값 설정
- worker 실행

처음 실행할 때는 PyTorch와 Hugging Face 모델 다운로드 때문에 시간이 걸릴 수 있습니다.

## 수동 실행

스크립트 없이 직접 실행해야 할 때는 아래 예시를 사용합니다.

macOS / Linux:

```bash
cd moderation-worker
python3.11 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -r requirements.txt

export REDIS_HOST=localhost REDIS_PORT=6379 MOD_QUEUE_KEY=mod:queue ROOM_CHANNEL_PREFIX=room: \
  DB_URL="jdbc:mysql://localhost:3306/chatguard_dev?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" \
  DB_USER=root DB_PASSWORD=chatguard1234 \
  MODERATOR_MODE=real UNSMILE_MODEL_ID=smilegate-ai/kor_unsmile MODEL_VERSION=unsmile-weighted-v1 \
  BLUR_THRESHOLD=0.40 CLEAN_PENALTY=0.10 TOKENIZERS_PARALLELISM=false

.venv/bin/python worker.py
```

## 주요 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `REDIS_HOST` | `localhost` | Redis 호스트 |
| `REDIS_PORT` | `6379` | Redis 포트 |
| `REDIS_PASSWORD` | 없음 | Redis 인증 비밀번호 |
| `MOD_QUEUE_KEY` | `mod:queue` | 검열 작업 큐 |
| `PROCESSING_QUEUE_KEY` | `mod:queue:processing` | 처리 중 작업 큐 |
| `DLQ_QUEUE_KEY` | `mod:queue:dlq` | 실패 작업 큐 |
| `MAX_RETRY_COUNT` | `3` | 작업 실패 시 최대 재시도 횟수 |
| `PROCESSING_TIMEOUT_SECONDS` | `300` | 처리 중 작업 복구 기준 시간 |
| `RECOVER_PROCESSING_ON_STARTUP` | `true` | 시작 시 오래된 처리 중 작업 복구 여부 |
| `ROOM_CHANNEL_PREFIX` | `room:` | Redis room 채널 prefix |
| `DB_URL` | `jdbc:mysql://localhost:3306/chatguard_dev?...` | MySQL 접속 URL |
| `DB_USER` | `root` | MySQL 사용자 |
| `DB_PASSWORD` | `backend/.env` 값 | MySQL 비밀번호 |
| `DB_POOL_MAX_CONNECTIONS` | `5` | worker 프로세스당 MySQL 커넥션 풀 최대 크기 |
| `MODERATOR_MODE` | `real` | `real` 또는 `mock` |
| `UNSMILE_MODEL_ID` | `smilegate-ai/kor_unsmile` | Hugging Face 모델 ID |
| `MODEL_VERSION` | `unsmile-weighted-v1` | 검열 로그에 저장할 모델 버전 |
| `BLUR_THRESHOLD` | `0.40` | 이 점수 이상이면 `BLOCK` |
| `CLEAN_PENALTY` | `0.10` | clean 점수 보정값 |

DLQ replay CronJob 전용 환경변수:

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DLQ_REPLAY_LIMIT` | `20` | 한 번에 replay 후보로 볼 최대 job 수 |
| `DLQ_REPLAY_APPLY` | `false` | `true`일 때만 DLQ에서 제거하고 main queue로 재투입 |
| `DLQ_REPLAY_MESSAGE_ID` | 없음 | 특정 `message_id`만 replay |
| `DLQ_REPLAY_ALLOWED_ERRORS` | 없음 | replay를 허용할 `last_error` 부분 문자열 목록 |
| `DLQ_REPLAY_BLOCKED_ERRORS` | `message_id is required,...` | replay에서 제외할 `last_error` 부분 문자열 목록 |
| `DLQ_REPLAY_MAX_REPLAY_COUNT` | `3` | 같은 job replay 최대 횟수 |

다른 값을 쓰려면 실행 전에 환경변수를 먼저 지정합니다.

Windows PowerShell:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3307/chatguard_dev"
$env:REDIS_HOST="localhost"
.\run-model.ps1
```

macOS / Linux:

```bash
DB_URL="jdbc:mysql://localhost:3307/chatguard_dev" REDIS_HOST="localhost" ./run-model.sh
```

## 동작 확인

### Metrics 확인

worker는 `prometheus_client`로 8000번 포트에 `/metrics`를 엽니다.

Windows PowerShell:

```powershell
Invoke-RestMethod http://localhost:8000/metrics
```

macOS / Linux:

```bash
curl -s http://localhost:8000/metrics | grep moderation_jobs_total
```

주요 지표:

- `moderation_jobs_total{verdict="pass|block"}`
- `moderation_inference_seconds`
- `moderation_queue_wait_seconds`
- `moderation_e2e_seconds`
- `moderation_retries_total`
- `moderation_dlq_total`
- `moderation_recovered_processing_total`

### DB 결과 확인

MySQL 콘솔 접속 시 한글이 깨지지 않도록 `utf8mb4` 옵션을 붙입니다.

```powershell
docker exec -it chatguard-mysql mysql --default-character-set=utf8mb4 -uroot -p
```

MySQL 접속 후:

```sql
USE chatguard_dev;

SELECT id, content, status, created_at
FROM messages
ORDER BY created_at DESC
LIMIT 10;

SELECT message_id, stage, verdict, score, model_version, reason, checked_at
FROM moderation_logs
ORDER BY id DESC
LIMIT 10;
```

정상 동작 기준:

- 정상 메시지는 `messages.status = VISIBLE`
- AI 검열 BLOCK 메시지는 `messages.status = BLURRED`
- 검열 결과는 `moderation_logs.stage = AI`로 저장
- worker 로그에 `inspect message_id=... action=... score=...` 출력

## DLQ Replay CronJob

worker는 실패한 job을 즉시 버리지 않고 재시도합니다. `MAX_RETRY_COUNT`를 초과한 job은 `DLQ_QUEUE_KEY`(`mod:queue:dlq`)로 이동합니다.

DLQ에 들어간 job은 잘못된 payload나 존재하지 않는 메시지처럼 다시 실행해도 실패할 job이 섞일 수 있습니다. 운영 환경에서는 Kubernetes CronJob이 1분마다 replay 가능한 job만 제한적으로 `mod:queue`로 되돌립니다.

로컬 스크립트 기본 설정은 dry-run입니다.

```yaml
- name: DLQ_REPLAY_APPLY
  value: "false"
```

CronJob에서는 `DLQ_REPLAY_APPLY=true`로 실행합니다. 운영에서는 `DLQ_REPLAY_ALLOWED_ERRORS`를 설정해 일시 장애 계열만 replay하는 것을 권장합니다.

```yaml
- name: DLQ_REPLAY_ALLOWED_ERRORS
  value: "MySQLError,RedisError,TimeoutError,Connection"
```

처리 방식:

```text
1분마다 DLQ 조회
-> 구조적으로 유효한 job인지 확인(message_id, room_id)
-> blocked error 제외
-> allowed error 조건 확인
-> replay_count 상한 확인
-> DLQ에서 제거하고 retry_count/last_error/failed_at 제거
-> mod:queue로 재투입
```

## 종료

worker 종료:

```powershell
Ctrl + C
```

가상환경 비활성화:

```powershell
deactivate
```

Docker 컨테이너 종료는 `backend` 디렉터리에서 실행합니다.

```powershell
docker compose down       # 컨테이너만 종료, DB 데이터 유지
docker compose down -v    # 볼륨까지 삭제, DB 데이터 초기화
```

## 주의사항

### MySQL 8.0 인증

MySQL 8.0의 기본 인증 방식인 `caching_sha2_password`를 PyMySQL이 처리하려면 `cryptography` 패키지가 필요합니다. `requirements.txt`에 포함되어 있으며, 누락되면 DB 쓰기 단계에서 다음 오류가 발생할 수 있습니다.

```text
'cryptography' package is required for sha256_password or caching_sha2_password
```

이 경우 의존성을 다시 설치합니다.

```bash
.venv/bin/python -m pip install -r requirements.txt
```

### Apple Silicon

M-series Mac에서 MPS(GPU)가 감지되어도 worker는 `device` 인자를 넘기지 않아 CPU로 추론합니다. 한 문장 추론은 수십 ms 수준이라 로컬 개발에는 충분합니다.

## 문제 해결

PowerShell 실행 정책 때문에 스크립트 실행이 막히는 경우:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

`./run-model.sh: Permission denied`가 나는 경우:

```bash
chmod +x run-model.sh
```

worker가 실행되지만 검열 결과가 반영되지 않는 경우:

- MySQL 컨테이너가 실행 중인지 확인
- Redis 컨테이너가 실행 중인지 확인
- `backend/.env`의 `DB_PASSWORD`가 MySQL 컨테이너 비밀번호와 같은지 확인
- `MOD_QUEUE_KEY`가 백엔드 설정과 같은지 확인
- worker 터미널에 `inspect message_id=...` 로그가 찍히는지 확인

부하 테스트 중 MySQL connection이 부족한 경우:

- worker는 프로세스마다 최대 `DB_POOL_MAX_CONNECTIONS`개까지 MySQL 연결을 재사용합니다.
- worker 레플리카 수와 MySQL `max_connections`를 함께 보고 값을 조정합니다.
