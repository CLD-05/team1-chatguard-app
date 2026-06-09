import { useEffect, useRef, useState } from 'react'
import { useParams, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import useChat from '../hooks/useChat'
import MessageItem from '../components/chat/MessageItem'
import ChatInput from '../components/chat/ChatInput'

export default function ChatPage() {
  const { id }          = useParams()
  const { user, token, logout } = useAuth()
  const navigate        = useNavigate()
  const roomId          = Number(id)

  const { messages, connected, sendMessage, loadMore, hasMore } = useChat({
    roomId,
    token,
    userId:      user?.id ?? 0,
    displayName: user?.display_name ?? '익명',
  })

  const bottomRef    = useRef(null)
  const containerRef = useRef(null)
  const [autoScroll, setAutoScroll] = useState(true)

  useEffect(() => {
    if (autoScroll) bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, autoScroll])

  function handleScroll() {
    const el = containerRef.current
    if (!el) return
    setAutoScroll(el.scrollHeight - el.scrollTop - el.clientHeight < 80)
  }

  if (!token) return <Navigate to="/" replace />
  if (!id || isNaN(roomId)) return <Navigate to="/home" replace />

  const visibleMessages = messages.filter((m) => m.status !== 'DELETED')

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
          <span className="font-semibold text-white text-sm">채팅방 #{roomId}</span>
          <span className="text-gray-500 text-xs">{connected ? '연결됨' : '재연결 중...'}</span>
          <div className="flex-1" />
          <span className="text-xs text-gray-500">{user?.display_name}</span>
        </header>

        <div
          ref={containerRef}
          onScroll={handleScroll}
          className="flex-1 overflow-y-auto py-2 flex flex-col"
        >
          {hasMore && (
            <button
              onClick={loadMore}
              className="text-xs text-gray-500 hover:text-indigo-400 transition-colors mx-auto mb-2 px-3 py-1 border border-gray-700 hover:border-indigo-500 rounded-full"
            >
              이전 메시지 불러오기
            </button>
          )}

          {visibleMessages.length === 0 && (
            <p className="text-center text-gray-600 text-xs py-8">채팅을 시작해보세요!</p>
          )}

          {visibleMessages.map((msg) => (
            <MessageItem key={msg.id} message={msg} isOwn={msg.user_id === user?.id} />
          ))}

          <div ref={bottomRef} />
        </div>

        {!autoScroll && (
          <div className="flex justify-center pb-1">
            <button
              onClick={() => { setAutoScroll(true); bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }}
              className="text-xs bg-indigo-600 hover:bg-indigo-500 text-white px-3 py-1 rounded-full transition-colors"
            >
              ↓ 최신 채팅으로
            </button>
          </div>
        )}

        <ChatInput onSend={sendMessage} disabled={!connected} />
      </div>
    </div>
  )
}
