// 스모크 — VU 3 · 1분. 로그인 → WS 접속 → chat.send → self-echo 왕복만 검증.
// 실행: K6_HOST=... K6_USERNAME=... K6_PASSWORD=... k6 run load-test/smoke.js
import { sleep } from 'k6';
import { loginOnce, pickRoom, chatSession } from './lib/common.js';

export const options = {
  vus: 3,
  duration: '1m',
  thresholds: {
    login_success: ['rate==1'], // 로그인 성공 100%
    ws_connect_success: ['rate>0.99'], // WS 연결 성공률 >99%
    self_echo_total: ['count>=1'], // self-echo 최소 1건
  },
};

export default function () {
  const token = loginOnce();
  if (!token) {
    sleep(3); // 로그인 실패 시 폭주 방지 — 실패 자체는 threshold가 드러냄
    return;
  }
  chatSession({ token, roomId: pickRoom(), sessionMs: 10000, sendIntervalMs: null });
  sleep(1);
}
