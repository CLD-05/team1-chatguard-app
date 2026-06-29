# ChatGuard Moderation Worker

Python 기반 AI 검열 Worker. 로컬 개발 환경에서 UnSmile 모델을 실행하고, 검열 결과를 MySQL과 Redis에 직접 반영한다.

## 사전 준비

- Python 3.11 권장 (`torch==2.5.1`은 3.12+ wheel 지원이 제한적이고 3.14 wheel은 없다. 3.11 사용 권장)
- Docker / Docker Compose
- 백엔드의 MySQL, Redis 컨테이너 실행 필요
- `backend/.env` 생성 필요

Windows(PowerShell):

```powershell
cd backend
copy .env.example .env
# .env 파일에서 DB_PASSWORD 값을 설정
docker compose up -d
```

macOS / Linux(bash):

```bash
cd backend
cp .env.example .env
# .env 파일에서 DB_PASSWORD 값을 설정
docker compose up -d
```

`backend/.env`는 커밋하지 않는다. worker 실행 스크립트는 기본적으로 `backend/.env`의 `DB_PASSWORD` 값을 읽어 MySQL에 접속한다.

## 처리 흐름

```text
프론트엔드 채팅 전송
-> 백엔드가 messages 테이블에 메시지 저장
-> 백엔드가 Redis mod:queue에 검열 작업 등록
-> worker가 Redis 큐에서 작업 조회
-> UnSmile 모델로 메시지 검열
-> worker가 moderation_logs에 AI 검열 로그 저장
-> BLOCK이면 worker가 messages.status를 BLURRED로 변경
-> BLOCK이면 worker가 Redis room:{room_id}에 moderation.hide publish
-> 백엔드가 Redis pub/sub 메시지를 WebSocket 클라이언트에 전달
```

v1에서는 AI 검열 결과로 `delete`를 발행하지 않고 `blur`만 발행한다.

## Python 환경 설정 및 실행

`moderation-worker` 디렉터리에서 OS에 맞는 실행 스크립트 하나만 실행한다.

Windows(PowerShell):

```powershell
cd moderation-worker
.\run-model.ps1
```

macOS / Linux(bash):

```bash
cd moderation-worker
./run-model.sh
```

각 스크립트는 다음을 자동 처리한다.

- `.venv`가 없으면 생성 (macOS/Linux는 `python3.11` 우선 사용)
- `requirements.txt` 패키지 설치/업데이트
- `backend/.env`에서 `DB_PASSWORD` 로드
- Redis, MySQL, 모델 관련 환경변수 기본값 설정
- worker 실행

처음 실행할 때는 PyTorch와 Hugging Face 모델 다운로드 때문에 시간이 걸릴 수 있다.

> **MySQL 8.0 인증 주의**: MySQL 8.0의 기본 인증(`caching_sha2_password`)을 PyMySQL이 처리하려면
> `cryptography` 패키지가 필요하다. `requirements.txt`에 포함되어 있으며, 없으면 모델 판정은 되지만
> DB 쓰기 단계에서 `'cryptography' package is required ...` 오류가 발생한다.

> **Apple Silicon(M-series) 참고**: MPS(GPU)가 감지되어도 worker는 `device` 인자를 넘기지 않아 CPU로 추론한다.
> 한 문장 추론은 수십 ms 수준이라 로컬 개발에는 충분하다.

### 스크립트 없이 수동 실행 (macOS / Linux)

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

## 기본 환경변수

```text
REDIS_HOST=localhost
REDIS_PORT=6379
MOD_QUEUE_KEY=mod:queue
PROCESSING_QUEUE_KEY=mod:queue:processing
DLQ_QUEUE_KEY=mod:queue:dlq
MAX_RETRY_COUNT=3
PROCESSING_TIMEOUT_SECONDS=300
RECOVER_PROCESSING_ON_STARTUP=true
ROOM_CHANNEL_PREFIX=room:

DB_URL=jdbc:mysql://localhost:3306/chatguard_dev?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USER=root
DB_PASSWORD=backend/.env 값 사용
DB_POOL_MAX_CONNECTIONS=5

MODERATOR_MODE=real
UNSMILE_MODEL_ID=smilegate-ai/kor_unsmile
MODEL_VERSION=unsmile-weighted-v1
BLUR_THRESHOLD=0.40
CLEAN_PENALTY=0.10
```

다른 값을 쓰려면 실행 전에 환경변수를 먼저 지정한다.

Windows(PowerShell):

```powershell
$env:DB_URL="jdbc:mysql://localhost:3307/chatguard_dev"
$env:REDIS_HOST="localhost"
.\run-model.ps1
```

macOS / Linux(bash):

```bash
DB_URL="jdbc:mysql://localhost:3307/chatguard_dev" REDIS_HOST="localhost" ./run-model.sh
```


## DB 결과 확인

MySQL 콘솔 접속 시 한글이 깨지지 않도록 `utf8mb4` 옵션을 붙인다.

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

## 종료

worker 종료:

```powershell
Ctrl + C
```

가상환경 비활성화:

```powershell
deactivate
```

Docker 컨테이너 종료는 `backend` 디렉터리에서 실행한다.

```powershell
docker compose down       # 컨테이너만 종료, DB 데이터 유지
docker compose down -v    # 볼륨까지 삭제, DB 데이터 초기화
```

## 문제 해결

PowerShell 실행 정책 때문에 스크립트 실행이 막히는 경우(Windows):

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

`./run-model.sh: Permission denied`가 나는 경우(macOS / Linux):

```bash
chmod +x run-model.sh
```

`'cryptography' package is required for ... auth methods` 오류가 나는 경우:

- MySQL 8.0의 `caching_sha2_password` 인증에는 `cryptography` 패키지가 필요하다.
- `.venv/bin/python -m pip install -r requirements.txt`로 의존성을 다시 설치한다.

worker가 실행되지만 검열 결과가 반영되지 않는 경우:

- MySQL 컨테이너가 실행 중인지 확인
- Redis 컨테이너가 실행 중인지 확인
- `backend/.env`의 `DB_PASSWORD`가 MySQL 컨테이너 비밀번호와 같은지 확인
- `MOD_QUEUE_KEY`가 백엔드 설정과 같은지 확인
- worker 터미널에 `inspect message_id=...` 로그가 찍히는지 확인

부하 테스트 중 MySQL connection이 부족한 경우:

- worker는 프로세스마다 최대 `DB_POOL_MAX_CONNECTIONS`개까지 MySQL 연결을 재사용한다.
- 워커 레플리카 수와 MySQL `max_connections`를 함께 보고 값을 조정한다.
