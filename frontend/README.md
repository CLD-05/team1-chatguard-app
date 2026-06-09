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

# 개발 서버 실행
npm run dev
```

개발 서버: `http://localhost:3000`

## Mock 모드

백엔드 없이도 동작 확인이 가능합니다.

```js
// src/api/axios.js
export const USE_MOCK = true  // 백엔드 연결 시 false로 변경
```

**Mock 데이터**
- 채팅방 목록 3개 제공
- 메시지 히스토리 제공
- "바보", "멍청", "욕설", "나쁜말" 입력 시 3초 후 블러 처리 시뮬레이션

## 백엔드 연결

1. `src/api/axios.js` 에서 `USE_MOCK = false` 로 변경
2. 백엔드 서버 실행 (`http://localhost:8080`)
3. `npm run dev` 실행 — `/api`, `/ws` 요청은 자동으로 백엔드로 프록시됩니다.
