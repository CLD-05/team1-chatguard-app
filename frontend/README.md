# ChatGuard Frontend

ChatGuard 라이브 채팅 + 실시간 AI 검열 플랫폼의 프론트엔드입니다.

## 기술 스택

| 기술 | 버전 | 역할 |
|---|---|---|
| React | 19 | UI 컴포넌트 프레임워크 |
| Vite | 8 | 빌드 도구, 개발 서버 |
| React Router | 7 | 페이지 라우팅 |
| Axios | 1 | REST API 호출 |
| Tailwind CSS | 4 | 스타일링 |

## 디렉토리 구조

```
src/
├─ api/
│   └─ axios.js             # REST 호출, 인터셉터, Mock 데이터
├─ assets/
├─ components/
│   └─ chat/
│       ├─ ChatInput.jsx    # 채팅 입력창
│       └─ MessageItem.jsx  # 메시지 렌더링 (블러 처리 포함)
├─ context/
│   └─ AuthContext.jsx      # 로그인 상태(JWT, 유저 정보) 전역 관리
├─ hooks/
│   └─ useChat.js           # WebSocket 연결, 메시지 수신/송신
├─ layouts/
│   └─ MainLayout.jsx       # 공통 헤더
├─ pages/
│   ├─ LoginPage.jsx        # 닉네임 로그인
│   ├─ HomePage.jsx         # 채팅방 목록
│   └─ ChatPage.jsx         # 실시간 채팅
└─ routes/
    └─ Router.jsx           # 라우팅, PrivateRoute
```

## 시작하기

```bash
# 의존성 설치
npm install

# 로컬 환경설정 파일 생성 (최초 1회)
cp .env.example .env.local

# 개발 서버 실행
npm run dev
```

개발 서버: `http://localhost:3000`

환경설정은 `.env.local`(개발자별, 커밋 안 됨)로 관리합니다. 설정 가능한 값은 `.env.example` 참고.

## Mock 모드

백엔드 없이도 동작을 확인할 수 있습니다. 모드는 코드가 아니라 `.env.local`로 토글합니다.

```bash
# .env.local
VITE_USE_MOCK=true    # 기본값 — 가짜 데이터로 동작 (변수 미설정 시에도 mock)
```

**Mock 데이터**
- 채팅방 목록 3개 제공
- 메시지 히스토리 제공
- "바보", "멍청", "욕설", "나쁜말" 입력 시 3초 후 블러 처리 시뮬레이션

## 백엔드 연결

로컬에서 프론트(3000) ↔ 백엔드(8080)를 붙여 개발하는 방법입니다. 프론트는 동일 출처(`localhost:3000`)로만 요청하고 Vite 프록시가 백엔드로 넘겨주므로 **CORS 설정이 필요 없습니다.**

1. 백엔드 서버를 먼저 실행합니다 (`http://localhost:8080`). 실행 방법은 `../backend/README.md` 참고.
2. `.env.local` 에서 mock을 끕니다.
   ```bash
   VITE_USE_MOCK=false
   ```
3. `npm run dev` 실행 — `/api`, `/ws` 요청은 `vite.config.js`의 프록시 설정에 따라 자동으로 백엔드(`localhost:8080`)로 전달됩니다.

> 프록시를 거치지 않고 백엔드를 직접 호출해야 하는 경우에만 `.env.local`에 `VITE_API_URL`(절대 URL)을 지정합니다. 이때는 백엔드에 CORS 설정이 필요합니다.
