# load-test — k6 부하 스크립트

프로토콜은 **7/02 선스캔 실코드 확인분** 기준 (DESIGN 서술과 다른 부분은 코드가 정답):

- 로그인: `POST /api/login` `{"username","password"}` → 응답 `token` (시드 계정, 평문은 팀 내 별도 전달 — 커밋 금지)
- WS 접속: `ws://HOST/ws?room_id=N` + **`Sec-WebSocket-Protocol: <token>` 헤더** (쿼리 `?token=` 아님 — `frontend/src/hooks/useChat.js:127` 준거)
- 송신 봉투: `{"type":"chat.send","payload":{"room_id":N,"content":"..."}}` — `room_id`는 접속 방과 일치, content 1~500자
- 성공 판정: **self-echo** (자기가 보낸 `chat.message` 되받기, 별도 ack 없음)
- close 처리: `1013`(파드당 `WS_CONNECTION_CAP`=200 초과) → jittered backoff 후 재접속 / `1001`(드레인) → 재접속. 인증 실패는 close가 아니라 핸드셰이크 HTTP 401 거부.
- 방 샤딩: `K6_ROOMS`의 방에 VU 라운드로빈 분배, 수신 메시지는 카운트만(파싱 최소화) — 브로드캐스트 증폭 제어

## 실행

```sh
K6_HOST=127.0.0.1:8080 K6_USERNAME=<시드계정> K6_PASSWORD=<평문> k6 run load-test/smoke.js
```

| 파일 | 용도 |
|---|---|
| `smoke.js` | VU 3 · 1분 — 로그인 100%·WS 연결 >99%·self-echo ≥1 검증 |
| `scenario-a-connections.js` | 실험 A: WS 접속 램프업 → chat-server HPA scale-out/in |
| `scenario-b-messages.js` | 실험 B: 메시지 폭주 → `mod:queue` 적체 → KEDA worker scale-out |
| `lib/common.js` | 로그인 + WS 세션 공통 (위 프로토콜 구현) |

## env (자격증명·호스트 하드코딩 금지)

| 변수 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `K6_HOST` | ✅ | — | `host[:port]` (예: `127.0.0.1:8080`, ALB 도메인) |
| `K6_USERNAME` / `K6_PASSWORD` | ✅ | — | 시드 계정 자격증명 |
| `K6_TLS` | | `0` | `1`이면 https/wss |
| `K6_ROOMS` | | `1,2,3` | VU를 분배할 방 id 목록(시드 방 1·2·3) |
| `K6_SESSION_SECONDS` / `K6_SEND_INTERVAL_SECONDS` | | 시나리오별 | WS 세션 길이 / 송신 간격 |
| `K6_MAX_VUS` `K6_RAMP` `K6_HOLD` `K6_DOWN` | | A: 300/5m/10m/5m | 실험 A 램프 파라미터 |
| `K6_VUS` `K6_DURATION` | | B: 50/15m | 실험 B 고정 VU 파라미터 |
