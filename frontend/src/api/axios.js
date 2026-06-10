import axios from 'axios'

// 기본값은 상대경로 '/api' — Vite 프록시(vite.config.js)를 통해 백엔드로 전달되며
// 동일 출처라 CORS가 필요 없다. VITE_API_URL을 지정하면 백엔드로 직접 호출한다(이 경우 백엔드 CORS 필요).
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('cg_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.clear()
      window.location.href = '/'
    }
    return Promise.reject(error)
  }
)

// ─── Mock 설정 ───────────────────────────────
// 개발자별 로컬 설정(.env.local)으로 토글한다. 기본값은 mock(true).
// 백엔드에 붙여 개발할 때만 .env.local에 VITE_USE_MOCK=false 를 둔다.
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const MOCK_ROOMS = [
  { id: 1, name: 'LCK 결승전 채팅방', streamer_name: '페이커',    created_at: '2026-06-08T10:00:00Z' },
  { id: 2, name: '일상 방송 채팅방',  streamer_name: '스트리머A', created_at: '2026-06-08T11:00:00Z' },
  { id: 3, name: '게임 방송 채팅방',  streamer_name: '스트리머B', created_at: '2026-06-08T12:00:00Z' },
]

const MOCK_MESSAGES = [
  { id: '01J000000000000000000001', room_id: 1, user_id: 2, display_name: '관전러',   content: '경기 시작했다!!',         created_at: '2026-06-08T12:00:00Z', status: 'VISIBLE' },
  { id: '01J000000000000000000002', room_id: 1, user_id: 3, display_name: '팬1호',    content: '페이커 진짜 미쳤다 ㅋㅋ', created_at: '2026-06-08T12:00:05Z', status: 'VISIBLE' },
  { id: '01J000000000000000000003', room_id: 1, user_id: 4, display_name: '악성유저', content: 'AI가 블러 처리한 메시지', created_at: '2026-06-08T12:00:10Z', status: 'BLURRED' },
  { id: '01J000000000000000000004', room_id: 1, user_id: 1, display_name: '나',        content: '오늘 경기 너무 재밌다!',  created_at: '2026-06-08T12:00:15Z', status: 'VISIBLE' },
]

export async function login(username) {
  if (USE_MOCK) {
    await delay(500)
    return { user: { id: 1, username, display_name: username }, token: 'mock-jwt-token' }
  }
  const res = await api.post('/login', { username })
  // 백엔드 응답: { user_id, access_token } → 프론트 공통 형태 { user, token }로 정규화
  const { user_id, access_token } = res.data
  return {
    user: { id: user_id, username, display_name: username },
    token: access_token,
  }
}

export async function getRooms() {
  if (USE_MOCK) {
    await delay(300)
    return MOCK_ROOMS
  }
  const res = await api.get('/rooms')
  return res.data
}

export async function getMessages(roomId, before) {
  if (USE_MOCK) {
    await delay(200)
    if (before) return []
    return MOCK_MESSAGES.filter((m) => m.room_id === roomId)
  }
  const params = { limit: 50, ...(before && { before }) }
  const res = await api.get(`/rooms/${roomId}/messages`, { params })
  return res.data
}

// ─── Mock WebSocket ───────────────────────────
let mockWsHandler = null

export function setMockWsHandler(handler) {
  mockWsHandler = handler
}

export function simulateModerationHide(messageId) {
  setTimeout(() => {
    mockWsHandler?.({ type: 'moderation.hide', payload: { id: messageId, action: 'blur' } })
  }, 3000)
}

const delay = (ms) => new Promise((r) => setTimeout(r, ms))

export default api
