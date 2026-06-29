import { useState, useEffect, useRef, useCallback } from 'react'
import { getMessages, setMockWsHandler, simulateModerationHide, USE_MOCK } from '../api/axios'


const MAX_MESSAGES = 500

const WS_BASE = import.meta.env.VITE_WS_BASE_URL
  ?? (import.meta.env.DEV
    ? 'ws://127.0.0.1:8080/ws'
    : `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`)
const MAX_RETRY_DELAY = 16_000

function mockUlid() {
  return 'MOCK' + Date.now().toString(36).toUpperCase().padStart(22, '0')
}

export default function useChat({ roomId, token, userId, displayName, onFatalError }) {
  const [messages, setMessages] = useState([])
  const [connected, setConnected] = useState(USE_MOCK)
  const [hasMore, setHasMore] = useState(true)
  const [wsError, setWsError] = useState(null) // { code, message }
  const [frozen, setFrozen] = useState(false)
  const [presence, setPresence] = useState({ count: 0, members: [] })

  const wsRef = useRef(null)
  const retryDelay = useRef(1_000)
  const unmounted = useRef(false)
  const connectionId = useRef(0)
  const reconnectTimer = useRef(null)
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
        if (combined.length > MAX_MESSAGES) {
          const trimmed = combined.slice(0, combined.length - MAX_MESSAGES)
          const visibleRemoved = trimmed.filter((m) => m.status !== 'DELETED').length
          trimmedRef.current += visibleRemoved
          return combined.slice(-MAX_MESSAGES)
        }
        return combined
      })
    }, 80)
    return () => clearInterval(t)
  }, [])

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
      const ws = new WebSocket(`${WS_BASE}?token=${token}&room_id=${roomId}`)
      wsRef.current = ws

      ws.onopen = () => {
        if (unmounted.current || connectionId.current !== currentConnectionId) { ws.close(); return }
        retryDelay.current = 1_000
        setConnected(true)
        setWsError(null)
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
        if (event.code === 1008) {
          // 인증·프로토콜 위반 — 재연결 없이 로그인 화면으로
          onFatalError?.()
          return
        }
        if (!unmounted.current) {
          // 1001(서버 드레인) 즉시 재연결, 그 외 jittered exponential backoff
          const delay = event.code === 1001 ? 0 : Math.random() * retryDelay.current
          reconnectTimer.current = setTimeout(connect, delay)
          if (event.code !== 1001) {
            retryDelay.current = Math.min(retryDelay.current * 2, MAX_RETRY_DELAY)
          } else {
            retryDelay.current = 1_000
          }
        }
      }
      ws.onerror = () => ws.close()
    }

    connect()
    return () => {
      unmounted.current = true
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current)
        reconnectTimer.current = null
      }
      wsRef.current?.close()
    }
  }, [roomId, token, handleEvent, onFatalError])

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

  return { messages, connected, sendMessage, loadMore, hasMore, wsError, clearWsError, frozen, presence, trimmedRef }
}
