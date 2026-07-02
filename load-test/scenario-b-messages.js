// 실험 B 골격 — 메시지 처리량 (mod:queue 적체 → KEDA worker 오토스케일).
// 연결 수는 cap 아래로 고정하고, VU당 송신율을 높여 큐를 채운다.
// 파라미터는 전부 env:
//   K6_VUS=50 K6_DURATION=15m
//   K6_SESSION_SECONDS=60 K6_SEND_INTERVAL_SECONDS=1 K6_ROOMS=1,2,3
import { sleep } from 'k6';
import { loginOnce, pickRoom, chatSession } from './lib/common.js';

export const options = {
  scenarios: {
    messages: {
      executor: 'constant-vus',
      vus: parseInt(__ENV.K6_VUS || '50', 10),
      duration: __ENV.K6_DURATION || '15m',
    },
  },
  thresholds: {
    login_success: ['rate==1'],
    ws_connect_success: ['rate>0.99'], // 연결 수가 cap 아래라 접속은 안정적이어야 함
  },
};

export default function () {
  const token = loginOnce();
  if (!token) {
    sleep(3);
    return;
  }
  chatSession({
    token,
    roomId: pickRoom(),
    sessionMs: parseInt(__ENV.K6_SESSION_SECONDS || '60', 10) * 1000,
    sendIntervalMs: parseInt(__ENV.K6_SEND_INTERVAL_SECONDS || '1', 10) * 1000,
  });
  sleep(1);
}
