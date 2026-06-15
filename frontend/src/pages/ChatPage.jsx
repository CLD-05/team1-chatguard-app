import { useEffect, useRef, useState, useCallback, useMemo } from 'react'
import { useParams, Navigate, useNavigate } from 'react-router-dom'
import { Virtuoso } from 'react-virtuoso'
import { useAuth } from '../context/auth-context'
import useChat from '../hooks/useChat'
import MessageItem from '../components/chat/MessageItem'
import ChatInput from '../components/chat/ChatInput'
import { getRoom } from '../api/axios'

const START_INDEX = 1_000_000

function ChatRoom({ roomId, user, token, logout, navigate }) {
  const [room, setRoom] = useState(null)

  const onFatalError = useCallback(() => { logout(); navigate('/') }, [logout, navigate])

  const { messages, connected, sendMessage, loadMore, hasMore, wsError, clearWsError } = useChat({
    roomId,
    token,
    userId:      user?.id ?? 0,
    displayName: user?.display_name ?? '익명',
    onFatalError,
  })

  useEffect(() => {
    if (!wsError) return
    const t = setTimeout(clearWsError, 3_000)
    return () => clearTimeout(t)
  }, [wsError, clearWsError])

  const virtuosoRef    = useRef(null)
  const userPausedRef  = useRef(false)
  const [atBottom, setAtBottom]           = useState(true)
  const [firstItemIndex, setFirstItemIndex] = useState(START_INDEX)

  useEffect(() => {
    getRoom(roomId).then(setRoom).catch(() => {})
  }, [roomId])

  useEffect(() => {
    if (import.meta.env.DEV) window.__chatMessages__ = messages
  }, [messages])

  const visibleMessages = useMemo(
    () => messages.filter((m) => m.status !== 'DELETED'),
    [messages],
  )

  const renderItem = useCallback(
    (_i, m) => <MessageItem message={m} isOwn={m.user_id === user?.id} />,
    [user?.id],
  )

  const followOutput = useCallback((isAtBottom) => {
    if (userPausedRef.current) return false
    return isAtBottom ? 'auto' : false
  }, [])

  const onStartReached = useCallback(async () => {
    if (!hasMore) return
    const older = await loadMore(visibleMessages[0]?.id)
    if (older.length) setFirstItemIndex((i) => i - older.length)
  }, [hasMore, loadMore, visibleMessages])

  return (
    <div className="h-screen flex bg-gray-950">

      {/* 사이드바 */}
      <aside className="w-14 bg-gray-900 border-r border-gray-800 flex flex-col items-center py-3 gap-3 shrink-0">
        <div className="w-9 h-9 rounded-xl bg-indigo-600 flex items-center justify-center">
          <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
          </svg>
        </div>

        <button
          onClick={() => navigate('/home')}
          title="채팅방 목록"
          className="w-9 h-9 rounded-xl bg-gray-800 hover:bg-gray-700 text-gray-400 hover:text-white flex items-center justify-center transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>

        <div className="flex-1" />

        <button
          onClick={() => { logout(); navigate('/') }}
          title="로그아웃"
          className="w-9 h-9 rounded-xl bg-gray-800 hover:bg-red-500/20 text-gray-500 hover:text-red-400 flex items-center justify-center transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
        </button>
      </aside>

      {/* 채팅 영역 */}
      <div className="flex-1 flex flex-col min-w-0">

        <header className="h-12 bg-gray-900 border-b border-gray-800 flex items-center px-4 gap-3 shrink-0">
          <span className={`w-2 h-2 rounded-full shrink-0 ${connected ? 'bg-green-400' : 'bg-yellow-400 animate-pulse'}`} />
          <span className="font-semibold text-white text-sm">
            {room ? room.name : `채팅방 #${roomId}`}
          </span>
          {room && <span className="text-gray-500 text-xs">{room.streamer_name}</span>}
          <span className="text-gray-500 text-xs">{connected ? '연결됨' : '재연결 중...'}</span>
          <div className="flex-1" />
          <span className="text-xs text-gray-500">{user?.display_name}</span>
        </header>

        {visibleMessages.length === 0 && (
          <p className="text-center text-gray-600 text-xs py-8 shrink-0">채팅을 시작해보세요!</p>
        )}

        <Virtuoso
          ref={(r) => { virtuosoRef.current = r; if (import.meta.env.DEV) window.__virtuoso__ = r }}
          onWheel={(e) => { if (e.deltaY < 0) userPausedRef.current = true }}
          data={visibleMessages}
          firstItemIndex={firstItemIndex}
          computeItemKey={(_, m) => m.id}
          itemContent={renderItem}
          followOutput={followOutput}
          atBottomThreshold={80}
          atBottomStateChange={(b) => { if (b) userPausedRef.current = false; setAtBottom(b) }}
          startReached={onStartReached}
          overscan={300}
          className="flex-1"
        />

        {!atBottom && (
          <div className="flex justify-center pb-1">
            <button
              onClick={() => {
                userPausedRef.current = false
                virtuosoRef.current?.scrollToIndex({ index: 'LAST', behavior: 'smooth' })
              }}
              className="text-xs bg-indigo-600 hover:bg-indigo-500 text-white px-3 py-1 rounded-full transition-colors"
            >
              ↓ 최신 채팅으로
            </button>
          </div>
        )}

        <ChatInput onSend={sendMessage} disabled={!connected} errorMessage={wsError?.message} />
      </div>
    </div>
  )
}

export default function ChatPage() {
  const { id }                  = useParams()
  const { user, token, logout } = useAuth()
  const navigate                = useNavigate()
  const roomId                  = Number(id)

  if (!token) return <Navigate to="/" replace />
  if (!id || isNaN(roomId)) return <Navigate to="/home" replace />

  return <ChatRoom roomId={roomId} user={user} token={token} logout={logout} navigate={navigate} />
}
