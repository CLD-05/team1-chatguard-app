// 실험 A 골격 — WS 접속 수 오토스케일 (ws_active_connections → chat-server HPA).
// 연결을 길게 유지하며 램프업하고, 메시지는 keepalive 수준으로 드물게 보낸다.
// 파라미터는 전부 env:
//   K6_MAX_VUS=300 K6_RAMP=5m K6_HOLD=10m K6_DOWN=5m
//   K6_SESSION_SECONDS=300 K6_SEND_INTERVAL_SECONDS=30 K6_ROOMS=1,2,3
import { sleep } from 'k6';
import { loginOnce, pickRoom, chatSession } from './lib/common.js';

const MAX_VUS = parseInt(__ENV.K6_MAX_VUS || '300', 10);

export const options = {
  scenarios: {
    connections: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.K6_RAMP || '5m', target: MAX_VUS },
        { duration: __ENV.K6_HOLD || '10m', target: MAX_VUS },
        { duration: __ENV.K6_DOWN || '5m', target: 0 }, // scale-in 관찰 구간 (D17)
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    login_success: ['rate==1'],
    // 파드당 WS_CONNECTION_CAP(=200) 초과분은 1013 거부→백오프 재접속이 정상 동작이라 느슨하게
    ws_connect_success: ['rate>0.90'],
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
    sessionMs: parseInt(__ENV.K6_SESSION_SECONDS || '300', 10) * 1000,
    sendIntervalMs: parseInt(__ENV.K6_SEND_INTERVAL_SECONDS || '30', 10) * 1000,
  });
  sleep(1);
}
