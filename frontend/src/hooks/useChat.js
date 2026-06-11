import { useState, useEffect, useRef, useCallback } from 'react'
import { getMessages, setMockWsHandler, simulateModerationHide, USE_MOCK } from '../api/axios'

const WS_BASE = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`
const MAX_RETRY_DELAY = 16_000

function mockUlid() {
  return 'MOCK' + Date.now().toString(36).toUpperCase().padStart(22, '0')
}

export default function useChat({ roomId, token, userId, displayName, onFatalError }) {
  const [messages, setMessages]   = useState([])
  const [connected, setConnected] = useState(false)
  const [hasMore, setHasMore]     = useState(true)
  const [wsError, setWsError]     = useState(null) // { code, message }

  const wsRef      = useRef(null)
  const retryDelay = useRef(1_000)
  const unmounted  = useRef(false)

  const handleEvent = useCallback((event) => {
    if (event.type === 'chat.message') {
      setMessages((prev) => [...prev, { ...event.payload, status: 'VISIBLE' }])
    } else if (event.type === 'moderation.hide') {
      const { id, action } = event.payload
      setMessages((prev) =>
        prev.map((m) =>
          m.id === id ? { ...m, status: action === 'delete' ? 'DELETED' : 'BLURRED' } : m
        )
      )
    } else if (event.type === 'error') {
      const code = event.payload?.code ?? 'INTERNAL'
      setWsError({ code, message: event.payload?.message ?? '오류가 발생했습니다' })
      if (code === 'ROOM_MISMATCH') {
        wsRef.current?.close()
      }
    } else if (event.type === 'server.closing') {
      wsRef.current?.close()
    }
  }, [])

  const loadMore = useCallback(async () => {
    const oldest = messages[0]?.id
    const history = await getMessages(roomId, oldest)
    if (history.length < 50) setHasMore(false)
    setMessages((prev) => [...history, ...prev])
  }, [messages, roomId])

  useEffect(() => {
    unmounted.current = false

    getMessages(roomId)
      .then((history) => {
        if (!unmounted.current) {
          setMessages(history.map((m) => ({ ...m, status: m.status ?? 'VISIBLE' })))
          if (history.length < 50) setHasMore(false)
        }
      })
      .catch(() => {
        if (!unmounted.current) setHasMore(false)
      })

    if (USE_MOCK) {
      setMockWsHandler(handleEvent)
      setConnected(true)
      return () => {
        unmounted.current = true
        setMockWsHandler(null)
      }
    }

    function connect() {
      if (unmounted.current) return
      const ws = new WebSocket(`${WS_BASE}?token=${token}&room_id=${roomId}`)
      wsRef.current = ws

      ws.onopen = () => {
        if (unmounted.current) { ws.close(); return }
        retryDelay.current = 1_000
        setConnected(true)
        setWsError(null)
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
          // 1001(서버 드레인) 즉시 재연결, 그 외 exponential backoff (jitter는 Week4)
          const delay = event.code === 1001 ? 0 : retryDelay.current
          setTimeout(connect, delay)
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
      wsRef.current?.close()
    }
  }, [roomId, token, handleEvent])

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

      const BAD_WORDS = ['바보', '멍청', '욕설', '나쁜말']
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

  return { messages, connected, sendMessage, loadMore, hasMore, wsError, clearWsError }
}
