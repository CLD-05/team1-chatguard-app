# ChatGuard Moderation Worker

Python 기반 AI Moderation Worker. 아래는 **로컬 개발 환경에서 UnSmile 모델을 실행하는 방법**이다.

## 사전 준비

- Python 3.11 권장
- Redis 실행 필요
- Spring Boot 백엔드 실행 필요
- `moderation-worker/.venv` 생성
- `frontend/src/api/axios.js`에서 백엔드 연결 모드 사용

  ```js
  export const USE_MOCK = false
  ```

AI 모델은 Spring Boot 백엔드 안에서 직접 실행되지 않는다. 백엔드는 메시지를 저장하고 Redis 큐에 검열 작업을 넣으며, Python worker가 Redis 큐에서 작업을 꺼내 UnSmile 모델로 검열한 뒤 결과를 백엔드로 다시 전송한다.

전체 흐름은 다음과 같다.

```text
프론트엔드 채팅 전송
-> 백엔드가 MySQL messages 테이블에 메시지 저장
-> 백엔드가 Redis mod:queue에 검열 작업 등록
-> Python worker가 Redis 큐에서 메시지 조회
-> UnSmile 모델로 메시지 검열
-> worker가 백엔드 API로 검열 결과 전송
-> 백엔드가 moderation_logs 저장 및 messages.status 업데이트
```

## Python 환경 설정

`moderation-worker` 디렉터리에서:

```powershell
cd moderation-worker

# 1) Python 가상환경 생성
python -m venv .venv

# 2) 가상환경 활성화
.\.venv\Scripts\Activate.ps1

# 3) 패키지 설치
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

- `requirements.txt`에는 Redis 연결, Hugging Face Transformers, PyTorch 관련 패키지가 포함되어 있다.
- `torch` 패키지 용량이 크기 때문에 처음 설치할 때 시간이 걸릴 수 있다.
- `.venv`는 `.gitignore` 대상이라 커밋하지 않는다.

## 실행 전 확인

worker를 실행하기 전에 MySQL, Redis, 백엔드가 먼저 실행되어 있어야 한다.

백엔드 디렉터리에서:

```powershell
cd backend
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Git Bash를 사용하는 경우:

```bash
cd backend
docker compose up -d
./mvnw spring-boot:run
```

백엔드 기동 확인:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

`{"status":"UP"}` 를 반환하면 정상이다.

Redis 기동 확인:

```powershell
docker exec chatguard-redis redis-cli PING
```

`PONG` 을 반환하면 정상이다.

## 실행 (Mock 모드)

AI 모델 다운로드 없이 Redis, 백엔드 연동 흐름만 빠르게 확인할 때 사용한다.

`moderation-worker` 디렉터리에서:

```powershell
cd moderation-worker
.\.venv\Scripts\Activate.ps1

$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_QUEUE_NAME="mod:queue"
$env:BACKEND_BASE_URL="http://localhost:8080"
$env:MODERATOR_MODE="mock"

python worker.py
```

기본 mock 검열 단어는 다음과 같다.

```text
바보, 멍청, 욕설, 민폐, 불쾌, 처참, 역겹
```

## 실행 (UnSmile 모델)

실제 AI 검열 모델을 사용할 때 실행한다.

`moderation-worker` 디렉터리에서:

```powershell
cd moderation-worker
.\.venv\Scripts\Activate.ps1

$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_QUEUE_NAME="mod:queue"
$env:BACKEND_BASE_URL="http://localhost:8080"
$env:MODERATOR_MODE="unsmile"
$env:UNSMILE_MODEL_ID="smilegate-ai/kor_unsmile"

python worker.py
```

- 첫 실행 시 Hugging Face에서 UnSmile 모델을 다운로드하므로 시간이 걸릴 수 있다.
- 한 번 다운로드된 모델은 로컬 캐시를 사용하므로 이후 실행은 더 빠르다.
- worker 터미널에 `worker started mode=unsmile` 로그가 나오면 실행 준비가 된 상태다.
- 메시지가 들어오면 `inspect message_id=...` 형태의 로그가 출력된다.

## 결과 확인

채팅 메시지를 보낸 뒤 MySQL에서 저장 여부를 확인한다.

```powershell
docker exec -it chatguard-mysql mysql -uroot -p
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

정상 동작 시:

- 채팅 메시지가 `messages` 테이블에 저장된다.
- worker가 검열 결과를 백엔드로 전송한다.
- 검열 결과가 `moderation_logs` 테이블에 저장된다.
- 부적절한 메시지는 `messages.status`가 `BLURRED` 또는 `DELETED`로 변경된다.

Redis 큐 길이를 확인하려면:

```powershell
docker exec chatguard-redis redis-cli LLEN mod:queue
```

worker가 실행 중이면 큐를 바로 소비하기 때문에 `0`이 나올 수 있다. 이 경우에도 worker 로그와 `moderation_logs` 테이블에 기록이 남아 있으면 정상이다.

## 종료

worker 종료:

```powershell
Ctrl + C
```

가상환경 비활성화:

```powershell
deactivate
```

백엔드와 Docker 컨테이너 종료는 `backend` 디렉터리에서:

```powershell
docker compose down       # 컨테이너만 내림 (DB 데이터 유지)
docker compose down -v    # 볼륨까지 삭제 (DB 데이터 초기화)
```

## 문제 해결

PowerShell에서 가상환경 활성화가 막히는 경우:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

채팅방 목록이 비어 있는 경우:

- `USE_MOCK=false` 상태에서는 프론트 mock 데이터가 아니라 실제 DB 데이터를 조회한다.
- DB에 채팅방이 없으면 화면이 비어 보일 수 있다.
- 백엔드의 `POST /api/rooms` API로 채팅방을 생성한 뒤 다시 확인한다.

worker가 실행됐는데 검열 결과가 안 생기는 경우:

- 백엔드가 `http://localhost:8080`에서 실행 중인지 확인한다.
- Redis 컨테이너가 실행 중인지 확인한다.
- `REDIS_QUEUE_NAME` 값이 백엔드 설정과 같은지 확인한다. 기본값은 `mod:queue`이다.
- worker 터미널에 `inspect message_id=...` 로그가 찍히는지 확인한다.
