# ChatGuard Backend (Chat Server)

Spring Boot 기반 Chat Server. 아래는 **로컬 개발 환경에서 실행하는 방법**이다.

## 사전 준비

- JDK 17
- Docker (Docker Compose v2 — `docker compose` 명령)
- `backend/.env` 생성 — `.env.example`를 복사해 `DB_PASSWORD`를 채운다.

  ```bash
  cd backend
  cp .env.example .env
  # .env 를 열어 DB_PASSWORD 값을 설정
  ```

  - `.env`는 `.gitignore` 대상이라 **커밋되지 않는다**(비밀번호 커밋 금지).
  - 이 값은 MySQL 컨테이너의 root 비밀번호이자 앱의 DB 접속 비밀번호로 함께 쓰인다.
  - 기본 활성 프로파일은 `local`이며, `.env`의 값이 `application.yml`의 `${...}`로 주입된다.

## 실행 (로컬)

`backend` 디렉터리에서:

```bash
cd backend

# 1) (선택) 기존 컨테이너·볼륨 정리 — 깨끗한 상태로 시작
docker compose down -v

# 2) MySQL · Redis 기동
docker compose up -d

# 3) 애플리케이션 실행
./mvnw spring-boot:run
```

- **1번 `down -v`** 는 로컬 DB 볼륨을 삭제하고 새로 띄운다. 스키마나 비밀번호가 꼬였을 때 안전하게 초기화하는 용도다. 보관해야 할 로컬 데이터가 있으면 생략한다.
- 기동 확인: <http://localhost:8080/actuator/health> 가 `{"status":"UP"}` 를 반환하면 정상.

## 종료

```bash
docker compose down       # 컨테이너만 내림 (DB 데이터 유지)
docker compose down -v    # 볼륨까지 삭제 (DB 데이터 초기화)
```
