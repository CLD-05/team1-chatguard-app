// 시연용 트래픽 생성 스크립트 — 59초 인터뷰 영상에 맞춘 2단계 채팅 시나리오.
// 0~30초(박항서 인터뷰): 소수 인원이 뜨문뜨문 채팅
// 30~59초(홍명보 등장): 접속자 급증 + 욕설 위주로 폭주
// 팀원 소유 lib/common.js·scenario-*.js는 건드리지 않고 완전히 독립된 파일로 분리했다.
//
// 보통은 demo-trigger.js가 이 스크립트를 대신 실행한다(K6_HOST·K6_TEST_START 자동 주입).
// K6_PASSWORD는 파일에 넣지 않고 매번 실행 시 넘긴다(lib/common.js·README.md와 동일 컨벤션).
// 수동 실행 시 예시(Git Bash):
//   K6_HOST=<ALB 도메인 또는 127.0.0.1:8080> K6_PASSWORD=<viewer1~20 비밀번호> \
//   K6_TEST_START=$(node -e "console.log(Date.now())") \
//   k6 run load-test/demo-hmb.js
import http from 'k6/http';
import ws from 'k6/ws';
import { sleep } from 'k6';

// pickRoom()은 VU를 여러 방에 라운드로빈으로 흩뿌리는데(1/2/3번방 순환), 시연은
// 한 방(2번방)만 화면에 띄워놓고 보여줄 거라 그걸 쓰면 VU 대부분이 다른 방으로 새서
// 정작 보고 있는 방엔 1~2명만 들어온 것처럼 보인다. 시연 방 하나로 고정한다.
const ROOM_ID = Number(__ENV.K6_ROOM_ID || 2);

const HOST = __ENV.K6_HOST;
const TLS = __ENV.K6_TLS === '1';
const HTTP_BASE = `${TLS ? 'https' : 'http'}://${HOST}`;
const WS_BASE = `${TLS ? 'wss' : 'ws'}://${HOST}`;

// 계정 하나로만 접속하면 채팅창에 같은 닉네임이 도배돼서 "여러 명"처럼 안 보인다.
// V3__DemoAccounts.sql로 심어둔 viewer1~20(전부 동일 비밀번호)을 VU 번호로 순환 배정해
// 서로 다른 닉네임이 섞여 나오게 한다.
const DEMO_PASSWORD = __ENV.K6_PASSWORD;
function demoUsername() {
  return `viewer${((__VU - 1) % 20) + 1}`;
}

let cachedToken = null;
function loginAsViewer() {
  if (cachedToken) return cachedToken;
  const res = http.post(
    `${HTTP_BASE}/api/login`,
    JSON.stringify({ username: demoUsername(), password: DEMO_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  cachedToken = res.status === 200 ? res.json('token') : null;
  return cachedToken;
}

// 여러 VU가 "영상 시작 시각" 기준으로 같은 국면을 보게 하려면 고정 epoch가 필요하다.
// 안 넘기면 각 VU가 자기 시작 시점을 0초로 잡아서, 늦게 뜬 VU(홍명보 구간에 새로 추가되는 VU)의
// 국면 판정이 틀어진다.
const TEST_START = Number(__ENV.K6_TEST_START || Date.now());

// 검열 걸릴 정도(금칙어)는 아니지만 약간 날 선 반응 — 박항서 구간용.
const CALM_POOL = [
  '오 시작했다',
  '박항서 감독 좀 답답하네',
  '이번에도 결과가 영 별로였는데',
  '왜 맨날 핑계만 대는거 같지',
  '듣다보니 좀 실망이다',
  '이건 좀 아니지 않나',
  '감독 바꿔야되는거 아님?',
  '말을 왜 저렇게 하지',
  '기대된다',
  '조용하네 아직',
  '오늘따라 표정이 안좋으시네',
  '질문이 좀 날카롭다',
  '이 정도면 답변 준비 안 된듯',
  '기자들 질문 수위 보소',
  '분위기 심상치 않은데',
  '다음 시즌 얘기는 언제 나오지',
  '인터뷰 생각보다 기네',
  '한숨 쉬시는거 보이네',
  '아 이제 개명보 나오나',
  '개명보 언제 나옴',
  '개명보 얼굴 보고싶다',
  '다음 순서가 개명보 맞지',
];

// 홍명보 구간은 절반은 검열 안 걸리는 반응, 절반은 실제 금칙어로 검열 걸리는 문장으로 섞는다.
// AI 검열은 욕설 유무만 보는 게 아니라 비난/명령조 어조 자체도 toxic으로 잡는 걸 실측으로 확인함
// ("그만해라 좀", "진짜 어이가 없네" 같은 비속어 없는 문장도 블러됨) — 그래서 MILD 쪽은 비난/명령이
// 아니라 그냥 놀람·의문 같은 중립적 관전평으로 채운다.
const ANGRY_MILD = [
  '헐 진짜?',
  '이게 무슨 상황이야',
  '다들 왜 이렇게 흥분했지',
  '분위기 왜 이래',
  '설마설마했는데',
  '이거 실화냐',
  '오 이거 논란되겠는데',
  '다음 발언 궁금하다',
  '실검 올라가겠다 이거',
  '캡처 각인데',
  '분위기 왜 이렇게 싸해졌지',
  '지금 실시간으로 난리났네',
  '이거 기사 뜨겠는데',
  '다들 진정하고 좀 지켜보자',
  '이건 좀 예상 밖이다',
  '반응이 폭발적이네',
  '개명보 왜 저러냐',
  '개명보 진짜 문제있네',
  '이제 개명보 차례네',
  '개명보 표정 봐라',
];

const ANGRY_TOXIC = [
  '아 시발 진짜 어이없네',
  '씨발 저게 무슨 소리야',
  '와 진짜 역겹다',
  '시발 사퇴가 답이다',
  '씨발 뭐하자는거야 진짜',
  '시발 팬들 무시하냐',
  '진짜 씨발 실망이다',
  '시발 그만해라 좀',
  '아 씨발 어처구니가 없네',
  '시발 이게 나라냐',
  '씨발 진짜 답도 없다',
  '시발 축구협회 다 문제있네',
];

// 1차 키워드 즉시차단 확인용 — 로컬 DB 기준으로 확인된 단어(바보/멍청이/테스트금칙어/해킹)로 구성했다.
// prod는 S3에서 받아오는 실제 운영 금칙어 목록이라 이 단어들이 그대로 안 걸릴 수 있음 — prod 시연 전
// 관리자 탭 키워드 목록에서 실제로 등록돼 있는지 먼저 확인할 것.
// 즉시차단은 방 화면엔 안 보이고(발신자한테만 에러 응답), 관리자 탭 1차 검열 수치에만 반영된다.
const ANGRY_BLOCKED = [
  '저 사람 완전 바보 아니냐',
  '진짜 멍청이 짓거리네',
  '이건 그냥 테스트금칙어 수준이다',
  '이거 해킹당한거 아니야',
  '바보 감독 그만둬라',
  '멍청이 아니고서야 저런 말을 하나',
];

export const options = {
  scenarios: {
    demo: {
      executor: 'ramping-vus',
      startVUs: 3,
      stages: [
        { target: 3, duration: '30s' }, // 박항서 구간: 3명 유지, 뜨문뜨문
        { target: 15, duration: '4s' }, // 홍명보 등장: 급격히 늘어남
        { target: 15, duration: '25s' }, // 폭주 유지 (총 59초)
      ],
      gracefulRampDown: '0s',
    },
  },
};

export default function () {
  const token = loginAsViewer();
  if (!token) {
    sleep(1);
    return;
  }

  const elapsedSec = (Date.now() - TEST_START) / 1000;
  const isSurge = elapsedSec >= 30;
  const intervalMs = isSurge ? 3000 : 6000;

  const res = ws.connect(
    `${WS_BASE}/ws?room_id=${ROOM_ID}`,
    { headers: { 'Sec-WebSocket-Protocol': token } },
    (socket) => {
      socket.on('open', () => {
        const send = () => {
          let pool = CALM_POOL;
          if (isSurge) {
            const r = Math.random();
            if (r < 0.15) pool = ANGRY_BLOCKED; // 1차 즉시차단 (화면엔 안 보임, 관리자 수치용)
            else if (r < 0.40) pool = ANGRY_TOXIC; // 2차 AI 블러
            else pool = ANGRY_MILD; // 검열 안 걸림
          }
          const content = pool[Math.floor(Math.random() * pool.length)];
          socket.send(JSON.stringify({ type: 'chat.send', payload: { room_id: ROOM_ID, content } }));
        };
        send();
        socket.setInterval(send, intervalMs);
        // 세션을 짧게 끊어서 반복 접속 → 다음 iteration마다 국면(elapsedSec)을 재평가한다.
        socket.setTimeout(() => socket.close(1000), 4000);
      });
    },
  );
  if (!(res && res.status === 101)) sleep(1);
}
