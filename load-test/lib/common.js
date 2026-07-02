// k6 공통 라이브러리 — 로그인 + WS 채팅 세션
// 프로토콜은 7/02 선스캔 실코드 확인분 기준(DESIGN 서술과 다른 부분은 코드가 정답):
// - 토큰은 쿼리 ?token= 이 아니라 Sec-WebSocket-Protocol 헤더로 전달 (frontend useChat.js:127 준거)
// - 성공 판정 = self-echo(자기가 보낸 chat.message 되받기) — 서버는 별도 ack를 주지 않음
// - 서버 발신 close는 1013(파드당 WS_CONNECTION_CAP 초과)·1001(드레인)뿐 — 재접속으로 대응
import http from 'k6/http';
import ws from 'k6/ws';
import { sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

export const loginSuccess = new Rate('login_success');
export const wsConnectSuccess = new Rate('ws_connect_success');
export const selfEchoTotal = new Counter('self_echo_total');
export const wsMessagesReceived = new Counter('ws_messages_received_total');
export const chatSendTotal = new Counter('chat_send_total');
export const wsClose1013 = new Counter('ws_close_1013_capacity_total');
export const wsClose1001 = new Counter('ws_close_1001_drain_total');
export const wsErrorEvents = new Counter('ws_error_events_total');

const HOST = __ENV.K6_HOST; // 예: 127.0.0.1:8080 또는 ALB 도메인 — env로만 주입
const TLS = __ENV.K6_TLS === '1'; // TLS 종단 환경이면 K6_TLS=1 → https/wss
const HTTP_BASE = `${TLS ? 'https' : 'http'}://${HOST}`;
const WS_BASE = `${TLS ? 'wss' : 'ws'}://${HOST}`;

let cachedToken = null; // k6는 VU별 JS 런타임 → VU당 1회만 로그인 (JWT TTL 24h)

export function loginOnce() {
  if (cachedToken) return cachedToken;
  const res = http.post(
    `${HTTP_BASE}/api/login`,
    JSON.stringify({ username: __ENV.K6_USERNAME, password: __ENV.K6_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  const token = res.status === 200 ? res.json('token') : null;
  loginSuccess.add(!!token);
  cachedToken = token || null;
  return cachedToken;
}

// K6_ROOMS=1,2,3 → VU를 방에 라운드로빈 분배 (방 샤딩 — 브로드캐스트 증폭 제어)
export function pickRoom() {
  const rooms = (__ENV.K6_ROOMS || '1,2,3').split(',').map((s) => parseInt(s.trim(), 10));
  return rooms[(__VU - 1) % rooms.length];
}

// WS 세션 1회를 실행하고 종료까지 블로킹한다.
// 접속 직후 서버 수신 순서: room.freeze 1회 → presence.update 스냅샷 (별도 처리 없이 카운트만).
// opts: { token, roomId, sessionMs, sendIntervalMs(null이면 접속 직후 1회만 송신) }
export function chatSession(opts) {
  const marker = `k6 vu${__VU} it${__ITER} r${opts.roomId} t${Date.now()}`; // self-echo 식별자 (content ≤500자)
  let backoffMs = 0;

  const res = ws.connect(
    `${WS_BASE}/ws?room_id=${opts.roomId}`,
    { headers: { 'Sec-WebSocket-Protocol': opts.token } },
    (socket) => {
      socket.on('open', () => {
        sendChat(socket, opts.roomId, marker);
        if (opts.sendIntervalMs) {
          socket.setInterval(() => sendChat(socket, opts.roomId, marker), opts.sendIntervalMs);
        }
        socket.setTimeout(() => socket.close(1000), opts.sessionMs);
      });
      socket.on('message', (data) => {
        // 파싱 최소화: JSON.parse 없이 카운트 + 문자열 탐색만 (브로드캐스트 수신량이 크므로)
        wsMessagesReceived.add(1);
        if (data.indexOf(marker) !== -1) selfEchoTotal.add(1);
        else if (data.indexOf('"type":"error"') !== -1) wsErrorEvents.add(1);
      });
      socket.on('close', (code) => {
        if (code === 1013) {
          wsClose1013.add(1);
          backoffMs = 1000 + Math.random() * 2000; // jittered backoff — scale-out 대기 후 재접속
        } else if (code === 1001) {
          wsClose1001.add(1);
          backoffMs = 200 + Math.random() * 800;
        }
      });
      socket.on('error', () => {
        // 핸드셰이크 거부(HTTP 401/404)는 close frame이 아니라 res.status !== 101로 관측됨
      });
    },
  );

  wsConnectSuccess.add(!!res && res.status === 101);
  if (!(res && res.status === 101) && backoffMs === 0) backoffMs = 1000 + Math.random() * 1000;
  if (backoffMs > 0) sleep(backoffMs / 1000); // 재접속은 다음 iteration이 수행
}

function sendChat(socket, roomId, marker) {
  // payload.room_id는 접속한 방과 일치해야 함 — 불일치 시 서버가 error/ROOM_MISMATCH
  socket.send(JSON.stringify({ type: 'chat.send', payload: { room_id: roomId, content: marker } }));
  chatSendTotal.add(1);
}
