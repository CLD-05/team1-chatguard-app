import { useState, useEffect, useRef, useCallback } from 'react'
import { getMessages, setMockWsHandler, simulateModerationHide, USE_MOCK } from '../api/axios'

const WS_BASE = import.meta.env.VITE_WS_BASE_URL
  ?? (import.meta.env.DEV
    ? 'ws://127.0.0.1:8080/ws'
    : `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`)
const MAX_RETRY_DELAY = 16_000

function mockUlid() {
  return 'MOCK' + Date.now().toString(36).toUpperCase().padStart(22, '0')
}

export default function useChat({ roomId, token, userId, displayName }) {
  const [messages, setMessages]   = useState([])
  const [connected, setConnected] = useState(false)
  const [hasMore, setHasMore]     = useState(true)

  const wsRef      = useRef(null)
  const retryDelay = useRef(1_000)
  const unmounted  = useRef(false)
  const connectionId = useRef(0)
  const reconnectTimer = useRef(null)

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
    const currentConnectionId = connectionId.current + 1
    connectionId.current = currentConnectionId
    unmounted.current = false
    setConnected(false)
    if (reconnectTimer.current) {
      clearTimeout(reconnectTimer.current)
      reconnectTimer.current = null
    }

    getMessages(roomId).then((history) => {
      if (!unmounted.current && connectionId.current === currentConnectionId) {
        setMessages(history.map((m) => ({ ...m, status: m.status ?? 'VISIBLE' })))
        if (history.length < 50) setHasMore(false)
      }
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
      if (unmounted.current || connectionId.current !== currentConnectionId) return
      const ws = new WebSocket(`${WS_BASE}?token=${token}&room_id=${roomId}`)
      wsRef.current = ws

      ws.onopen = () => {
        if (unmounted.current || connectionId.current !== currentConnectionId) { ws.close(); return }
        retryDelay.current = 1_000
        setConnected(true)
      }
      ws.onmessage = (e) => {
        if (connectionId.current === currentConnectionId) {
          handleEvent(JSON.parse(e.data))
        }
      }
      ws.onclose = () => {
        if (connectionId.current === currentConnectionId) {
          setConnected(false)
        }
        if (!unmounted.current && connectionId.current === currentConnectionId) {
          reconnectTimer.current = setTimeout(connect, retryDelay.current)
          retryDelay.current = Math.min(retryDelay.current * 2, MAX_RETRY_DELAY)
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
  }, [roomId, token, handleEvent])

  const sendMessage = useCallback((content) => {
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

  return { messages, connected, sendMessage, loadMore, hasMore }
}
