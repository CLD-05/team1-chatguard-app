import { useState, useEffect, useRef, useCallback } from 'react'
import { getMessages, setMockWsHandler, simulateModerationHide, USE_MOCK } from '../api/axios'


const MAX_MESSAGES = 500
// 트림을 매 flush(80ms)마다 하면, 메시지 폭주 상황에서 "추가"와 "트림"이 거의 항상
// 동시에 일어나 Virtuoso가 끝에 새 항목이 붙는 것조차 제대로 인식 못 하는 문제가 생긴다.
// TRIM_THRESHOLD까지는 그냥 쌓이게 두고, 넘었을 때만 한 번에 MAX_MESSAGES로 정리해서
// 트림 발생 빈도 자체를 크게 낮춘다(추가만 일어나는 구간을 길게 확보).
const TRIM_THRESHOLD = 700

const WS_BASE = import.meta.env.VITE_WS_BASE_URL
  ?? (import.meta.env.DEV
    ? 'ws://127.0.0.1:8080/ws'
    : `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`)
const MAX_RETRY_DELAY = 16_000

function mockUlid() {
  return 'MOCK' + Date.now().toString(36).toUpperCase().padStart(22, '0')
}

export default function useChat({ roomId, token, userId, displayName, onFatalError, onTrim, enabled = true }) {
  const [messages, setMessages] = useState([])
  const [connected, setConnected] = useState(USE_MOCK)
  const [connectionStatus, setConnectionStatus] = useState(USE_MOCK ? 'CONNECTED' : 'RECONNECTING')
  const [hasMore, setHasMore] = useState(true)
  const [wsError, setWsError] = useState(null) // { code, message }
  const [frozen, setFrozen] = useState(false)
  const [presence, setPresence] = useState({ count: 0, members: [] })

  const wsRef = useRef(null)
  const retryDelay = useRef(1_000)
  const unmounted = useRef(false)
  const isOffline = useRef(false)
  const connectionId = useRef(0)
  const reconnectTimer = useRef(null)
  const stabilityTimer = useRef(null) // 5초 안정성 확인용 타이머 추가
  const bufferRef = useRef([])
  const isReconnect = useRef(false)
  const trimmedRef = useRef(0)

  const handleEvent = useCallback((event) => {
    if (event.type === 'chat.message') {
      bufferRef.current.push({ ...event.payload, status: 'VISIBLE' })
      return
    } else if (event.type === 'moderation.hide') {
      const { id, action } = event.payload
      // v1 워커는 'blur'만 발행. 'delete'는 향후 수동 모더레이션 대비 예약
      const newStatus = action === 'delete' ? 'DELETED' : 'BLURRED'
      // 버퍼에 아직 있는 메시지도 업데이트 (80ms 배치 전에 moderation.hide가 오는 경우)
      bufferRef.current = bufferRef.current.map((m) =>
        m.id === id ? { ...m, status: newStatus } : m
      )
      setMessages((prev) =>
        prev.map((m) => (m.id === id ? { ...m, status: newStatus } : m))
      )
    } else if (event.type === 'room.freeze') {
      setFrozen(event.payload?.frozen ?? false)
    } else if (event.type === 'presence.update') {
      setPresence({ count: event.payload?.count ?? 0, members: event.payload?.members ?? [] })
    } else if (event.type === 'error') {
      const code = event.payload?.code ?? 'INTERNAL'
      setWsError({ code, message: event.payload?.message ?? '오류가 발생했습니다' })
      if (code === 'ROOM_MISMATCH') {
        wsRef.current?.close()
      }
    }
  }, [])

  useEffect(() => {
    const t = setInterval(() => {
      if (!bufferRef.current.length) return
      const batch = bufferRef.current
      bufferRef.current = []
      setMessages((prev) => {
        const combined = [...prev, ...batch]
        if (combined.length > TRIM_THRESHOLD) {
          const trimmed = combined.slice(0, combined.length - MAX_MESSAGES)
          const visibleRemoved = trimmed.filter((m) => m.status !== 'DELETED').length
          trimmedRef.current += visibleRemoved
          return combined.slice(-MAX_MESSAGES)
        }
        return combined
      })
      // setMessages와 같은 타이머 틱 안에서 동기 호출 — React가 한 렌더로 배칭 처리해,
      // "메시지는 갱신됐는데 firstItemIndex는 아직 안 맞은" 렌더가 끼는 걸 막는다.
      onTrim?.(trimmedRef.current)
    }, 30)
    return () => clearInterval(t)
  }, [onTrim])

  const loadMore = useCallback(async (before) => {
    const history = await getMessages(roomId, before)
    if (history.length < 50) setHasMore(false)
    if (history.length) {
      setMessages((prev) => {
        const combined = [
          ...history.map((m) => ({ ...m, status: m.status ?? 'VISIBLE' })),
          ...prev,
        ]
        // 뒤에서 자르기: 오래된 메시지를 살리고 최신 메시지를 제거 (firstItemIndex 조정 불필요)
        return combined.length > MAX_MESSAGES ? combined.slice(0, MAX_MESSAGES) : combined
      })
    }
    return history
  }, [roomId])

  useEffect(() => {
    if (!enabled) {
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current)
        reconnectTimer.current = null
      }
      if (stabilityTimer.current) {
        clearTimeout(stabilityTimer.current)
        stabilityTimer.current = null
      }
      wsRef.current?.close()
      return
    }

    const currentConnectionId = connectionId.current + 1
    connectionId.current = currentConnectionId
    unmounted.current = false
    if (reconnectTimer.current) {
      clearTimeout(reconnectTimer.current)
      reconnectTimer.current = null
    }

    getMessages(roomId).then((history) => {
      if (!unmounted.current && connectionId.current === currentConnectionId) {
        setMessages(history.map((m) => ({ ...m, status: m.status ?? 'VISIBLE' })))
        if (history.length < 50) setHasMore(false)
      }
    }).catch(() => {
      if (!unmounted.current && connectionId.current === currentConnectionId) {
        setHasMore(false)
      }
    })

    if (USE_MOCK) {
      setMockWsHandler(handleEvent)
      return () => {
        unmounted.current = true
        setMockWsHandler(null)
      }
    }

    function connect() {
      if (unmounted.current || connectionId.current !== currentConnectionId) return
      setConnectionStatus('RECONNECTING')
      if (stabilityTimer.current) {
        clearTimeout(stabilityTimer.current)
        stabilityTimer.current = null
      }
      const ws = new WebSocket(`${WS_BASE}?room_id=${roomId}`, [token])
      wsRef.current = ws

      ws.onopen = () => {
        if (unmounted.current || connectionId.current !== currentConnectionId) { ws.close(); return }
        setConnected(true)
        setConnectionStatus('CONNECTED')
        setWsError(null)

        // 연결 수립 후 5초간 끊어지지 않고 유지될 때만 딜레이를 1초로 리셋
        stabilityTimer.current = setTimeout(() => {
          retryDelay.current = 1_000
          stabilityTimer.current = null
        }, 5000)

        if (isReconnect.current) {
          getMessages(roomId).then((latest) => {
            if (unmounted.current) return
            setMessages((prev) => {
              const latestMap = new Map(latest.map((m) => [m.id, m]))
              const updated = prev.map((m) => {
                const fresh = latestMap.get(m.id)
                return fresh ? { ...m, status: fresh.status ?? m.status } : m
              })
              const prevIds = new Set(prev.map((m) => m.id))
              const missed = latest
                .filter((m) => !prevIds.has(m.id))
                .map((m) => ({ ...m, status: m.status ?? 'VISIBLE' }))
              return [...updated, ...missed]
            })
          }).catch(() => {})
        }
        isReconnect.current = true
      }
      ws.onmessage = (e) => handleEvent(JSON.parse(e.data))
      ws.onclose = (event) => {
        setConnected(false)
        if (stabilityTimer.current) {
          // 5초 이내에 끊어졌으므로 안정성 타이머를 제거하고 백오프 딜레이는 유지
          clearTimeout(stabilityTimer.current)
          stabilityTimer.current = null
        }
        if (event.code === 1008) {
          setConnectionStatus('DISCONNECTED')
          // 인증·프로토콜 위반 — 재연결 없이 로그인 화면으로
          onFatalError?.()
          return
        }
        if (!unmounted.current && !isOffline.current) {
          setConnectionStatus('RECONNECTING')
          // 1001(서버 드레인) 즉시 재연결, 그 외 jittered exponential backoff
          const delay = event.code === 1001 ? 0 : Math.random() * retryDelay.current
          reconnectTimer.current = setTimeout(connect, delay)
          if (event.code !== 1001) {
            retryDelay.current = Math.min(retryDelay.current * 2, MAX_RETRY_DELAY)
          } else {
            retryDelay.current = 1_000
          }
        } else {
          setConnectionStatus('DISCONNECTED')
        }
      }
      ws.onerror = () => ws.close()
    }

    connect()

    const handleOnline = () => {
      if (unmounted.current) return
      isOffline.current = false
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current)
        reconnectTimer.current = null
      }
      retryDelay.current = 1_000
      connect()
    }
    const handleOffline = () => {
      if (unmounted.current) return
      isOffline.current = true
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current)
        reconnectTimer.current = null
      }
      if (stabilityTimer.current) {
        clearTimeout(stabilityTimer.current)
        stabilityTimer.current = null
      }
      setConnected(false)
      setConnectionStatus('DISCONNECTED')
      wsRef.current?.close()
    }
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    return () => {
      unmounted.current = true
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current)
        reconnectTimer.current = null
      }
      if (stabilityTimer.current) {
        clearTimeout(stabilityTimer.current)
        stabilityTimer.current = null
      }
      wsRef.current?.close()
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [roomId, token, handleEvent, onFatalError, enabled])

  const sendMessage = useCallback((content) => {
    setWsError(null)
    if (USE_MOCK) {
      const id = mockUlid()
      const msg = {
        id,
        room_id: roomId,
        user_id: userId,
        display_name: displayName,
        content,
        created_at: new Date().toISOString(),
        status: 'VISIBLE',
      }
      setMessages((prev) => [...prev, msg])

      const BAD_WORDS = ['바보', '멍청', '욕설', '민폐', '불쾌', '처참', '역겹', '나쁜말']
      if (BAD_WORDS.some((w) => content.includes(w))) {
        simulateModerationHide(id)
      }
      return
    }

    const ws = wsRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) return
    ws.send(JSON.stringify({ type: 'chat.send', payload: { room_id: roomId, content } }))
  }, [roomId, userId, displayName])

  const clearWsError = useCallback(() => setWsError(null), [])

  // 리렌더링 병목을 방지하는 React 권장 상태 유도(Derived State) 기법 적용
  const derivedConnected = enabled ? connected : false;
  const derivedConnectionStatus = enabled ? connectionStatus : 'DISCONNECTED';

  return {
    messages,
    connected: derivedConnected,
    connectionStatus: derivedConnectionStatus,
    sendMessage,
    loadMore,
    hasMore,
    wsError,
    clearWsError,
    frozen,
    presence,
    trimmedRef
  }
}
