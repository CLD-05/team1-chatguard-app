# ChatGuard Moderation Worker

Python 기반 AI 검열 Worker. 로컬 개발 환경에서 UnSmile 모델을 실행하고, 검열 결과를 MySQL과 Redis에 직접 반영한다.

## 사전 준비

- Python 3.11 권장
- Docker / Docker Compose
- 백엔드의 MySQL, Redis 컨테이너 실행 필요
- `backend/.env` 생성 필요

```powershell
cd backend
copy .env.example .env
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

`moderation-worker` 디렉터리에서 다음 명령만 실행한다.

```powershell
cd moderation-worker
.\run-unsmile.ps1
```

`run-unsmile.ps1`은 다음을 자동 처리한다.

- `.venv`가 없으면 생성
- `requirements.txt` 패키지 설치/업데이트
- `backend/.env`에서 `DB_PASSWORD` 로드
- Redis, MySQL, 모델 관련 환경변수 기본값 설정
- worker 실행

처음 실행할 때는 PyTorch와 Hugging Face 모델 다운로드 때문에 시간이 걸릴 수 있다.

## 기본 환경변수

```text
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_QUEUE_NAME=mod:queue
ROOM_CHANNEL_PREFIX=room:

DB_URL=jdbc:mysql://localhost:3306/chatguard_dev?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true
DB_USER=root
DB_PASSWORD=backend/.env 값 사용

MODERATOR_MODE=unsmile
UNSMILE_MODEL_ID=smilegate-ai/kor_unsmile
BLOCK_THRESHOLD=0.70
METRICS_PORT=8000
```

다른 값을 쓰려면 실행 전에 환경변수를 먼저 지정한다.

```powershell
$env:DB_URL="jdbc:mysql://localhost:3307/chatguard_dev"
$env:REDIS_HOST="localhost"
.\run-unsmile.ps1
```

## Metrics 확인

worker는 `prometheus_client`로 8000번 포트에 `/metrics`를 연다.

```powershell
Invoke-RestMethod http://localhost:8000/metrics
```

주요 지표:

- `moderation_jobs_total{verdict="pass|block"}`
- `moderation_inference_seconds`
- `moderation_queue_wait_seconds`
- `moderation_e2e_seconds`

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

PowerShell 실행 정책 때문에 스크립트 실행이 막히는 경우:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

worker가 실행되지만 검열 결과가 반영되지 않는 경우:

- MySQL 컨테이너가 실행 중인지 확인
- Redis 컨테이너가 실행 중인지 확인
- `backend/.env`의 `DB_PASSWORD`가 MySQL 컨테이너 비밀번호와 같은지 확인
- `REDIS_QUEUE_NAME`이 백엔드 설정과 같은지 확인
- worker 터미널에 `inspect message_id=...` 로그가 찍히는지 확인
