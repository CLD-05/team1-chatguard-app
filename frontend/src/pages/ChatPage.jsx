import { useEffect, useRef, useState, useCallback, useMemo } from 'react'
import { useParams, Navigate, useNavigate } from 'react-router-dom'
import { Virtuoso } from 'react-virtuoso'
import { useAuth } from '../context/auth-context'
import useChat from '../hooks/useChat'
import MessageItem from '../components/chat/MessageItem'
import ChatInput from '../components/chat/ChatInput'
import { getRoom } from '../api/axios'
import { freezeRoom } from '../api/admin'

const START_INDEX = 1_000_000

function toYoutubeEmbed(url) {
  if (!url) return null
  try {
    const u = new URL(url)
    let videoId = null
    if (u.hostname.includes('youtu.be')) {
      videoId = u.pathname.slice(1)
    } else if (u.hostname.includes('youtube.com')) {
      videoId = u.searchParams.get('v')
    }
    if (!videoId) return null
    return `https://www.youtube.com/embed/${videoId}?autoplay=1`
  } catch {
    return null
  }
}

function ChatRoom({ roomId, user, token, logout, navigate, isAdmin }) {
  const [room, setRoom] = useState(null)
  const [streamUrl, setStreamUrl] = useState('')
  const [urlInput, setUrlInput] = useState('')
  const [showUrlInput, setShowUrlInput] = useState(false)
  const [chatWidth, setChatWidth] = useState(320)

  const isDragging = useRef(false)
  const dragStartX = useRef(0)
  const dragStartWidth = useRef(0)

  const handleDragStart = useCallback((e) => {
    isDragging.current = true
    dragStartX.current = e.clientX
    dragStartWidth.current = chatWidth
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }, [chatWidth])

  useEffect(() => {
    function onMouseMove(e) {
      if (!isDragging.current) return
      const delta = dragStartX.current - e.clientX
      setChatWidth(Math.min(520, Math.max(220, dragStartWidth.current + delta)))
    }
    function onMouseUp() {
      if (!isDragging.current) return
      isDragging.current = false
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
    return () => {
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
    }
  }, [])

  const onFatalError = useCallback(() => { logout(); navigate('/') }, [logout, navigate])

  const { messages, connected, sendMessage, loadMore, hasMore, wsError, clearWsError, frozen } = useChat({
    roomId,
    token,
    userId: user?.id ?? 0,
    displayName: user?.display_name ?? '익명',
    onFatalError,
  })

  useEffect(() => {
    if (!wsError) return
    const t = setTimeout(clearWsError, 3_000)
    return () => clearTimeout(t)
  }, [wsError, clearWsError])

  const virtuosoRef = useRef(null)
  const userPausedRef = useRef(false)
  const [atBottom, setAtBottom] = useState(true)
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
    const visibleOlder = older.filter((m) => m.status !== 'DELETED')
    if (visibleOlder.length) setFirstItemIndex((i) => i - visibleOlder.length)
  }, [hasMore, loadMore, visibleMessages])

  const embedUrl = toYoutubeEmbed(streamUrl)

  function handleSetUrl() {
    setStreamUrl(urlInput.trim())
    setShowUrlInput(false)
  }

  return (
    <div className="h-screen flex flex-col bg-gray-950 overflow-hidden">

      {/* 헤더 */}
      <header className="h-11 bg-gray-900 border-b border-gray-800 flex items-center px-4 gap-3 shrink-0">
        <button
          onClick={() => navigate('/home')}
          className="text-gray-500 hover:text-white transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>

        <div className="w-px h-4 bg-gray-800" />

        <div className="w-6 h-6 rounded-md bg-indigo-600 flex items-center justify-center shrink-0">
          <svg className="w-3.5 h-3.5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.069A1 1 0 0121 8.87v6.26a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
          </svg>
        </div>

        <div className="flex items-center gap-2 min-w-0">
          <span className="font-semibold text-white text-sm truncate">
            {room?.name ?? `채팅방 #${roomId}`}
          </span>
          {room && <span className="text-gray-500 text-xs shrink-0">{room.streamer_name}</span>}
        </div>

        <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${connected ? 'bg-green-400' : 'bg-yellow-400 animate-pulse'}`} />

        <div className="flex-1" />

        {isAdmin && (
          <button
            onClick={() => setShowUrlInput((v) => !v)}
            className={`flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-md transition-colors ${showUrlInput ? 'bg-gray-700 text-white' : 'text-gray-500 hover:text-white hover:bg-gray-800'}`}
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
            </svg>
            URL
          </button>
        )}

        {isAdmin && (
          <button
            onClick={() => freezeRoom(roomId, !frozen).catch(() => {})}
            title={frozen ? '채팅 재개' : '채팅 얼리기'}
            className={`flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-md border transition-colors ${
              frozen
                ? 'bg-cyan-500/20 border-cyan-500/50 text-cyan-300 hover:bg-cyan-500/30'
                : 'border-gray-700 text-gray-500 hover:text-white hover:bg-gray-800 hover:border-gray-600'
            }`}
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <line x1="12" y1="2" x2="12" y2="22" />
              <line x1="2" y1="12" x2="22" y2="12" />
              <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
              <line x1="19.07" y1="4.93" x2="4.93" y2="19.07" />
              <circle cx="12" cy="12" r="2.5" fill="currentColor" stroke="none" />
            </svg>
            {frozen ? '재개' : '얼리기'}
          </button>
        )}

        <span className="text-xs text-gray-600 ml-1">{user?.display_name}</span>
        <button
          onClick={() => { logout(); navigate('/') }}
          className="text-gray-600 hover:text-red-400 transition-colors ml-1"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
        </button>
      </header>

      {/* URL 입력바 (어드민) */}
      {showUrlInput && (
        <div className="bg-gray-900 border-b border-gray-800 px-4 py-2 flex gap-2 shrink-0">
          <input
            type="text"
            value={urlInput}
            onChange={(e) => setUrlInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSetUrl()}
            placeholder="YouTube URL 입력 (예: https://www.youtube.com/watch?v=...)"
            className="flex-1 bg-gray-800 border border-gray-700 rounded-lg px-3 py-1.5 text-xs text-white placeholder-gray-600 focus:outline-none focus:border-indigo-500"
          />
          <button
            onClick={handleSetUrl}
            className="bg-indigo-600 hover:bg-indigo-500 text-white px-3 py-1.5 rounded-lg text-xs transition-colors"
          >
            적용
          </button>
          {streamUrl && (
            <button
              onClick={() => { setStreamUrl(''); setUrlInput('') }}
              className="text-gray-500 hover:text-red-400 px-2 py-1.5 rounded-lg text-xs transition-colors"
            >
              초기화
            </button>
          )}
        </div>
      )}

      {/* 본문: 영상(좌) + 채팅(우) */}
      <div className="flex flex-1 overflow-hidden">

        {/* 영상 영역 */}
        <div className="flex-1 flex flex-col bg-black overflow-hidden">
          {embedUrl ? (
            <iframe
              src={embedUrl}
              className="w-full flex-1"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
            />
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center gap-3 text-gray-700">
              <svg className="w-16 h-16 opacity-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M15 10l4.553-2.069A1 1 0 0121 8.87v6.26a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
              </svg>
              <p className="text-sm opacity-40">스트림 없음</p>
              {isAdmin && (
                <p className="text-xs opacity-25">상단 URL 버튼으로 YouTube 링크를 설정하세요</p>
              )}
            </div>
          )}

          {/* 방 정보 바 */}
          <div className="bg-gray-900 border-t border-gray-800 px-4 py-2.5 shrink-0">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-full bg-indigo-600 flex items-center justify-center shrink-0">
                <span className="text-white text-xs font-bold">
                  {room?.streamer_name?.[0]?.toUpperCase() ?? 'S'}
                </span>
              </div>
              <div>
                <p className="text-sm font-semibold text-white leading-tight">
                  {room?.name ?? `채팅방 #${roomId}`}
                </p>
                <p className="text-xs text-gray-500">{room?.streamer_name}</p>
              </div>
              <div className="ml-auto flex items-center gap-1.5">
                <span className={`w-1.5 h-1.5 rounded-full ${connected ? 'bg-red-500 animate-pulse' : 'bg-gray-600'}`} />
                <span className="text-xs text-gray-500">{connected ? 'LIVE' : 'OFFLINE'}</span>
              </div>
            </div>
          </div>
        </div>

        {/* 드래그 핸들 */}
        <div
          onMouseDown={handleDragStart}
          className="w-1 bg-gray-800 hover:bg-indigo-600 cursor-col-resize shrink-0 transition-colors"
        />

        {/* 채팅 패널 */}
        <div style={{ width: chatWidth }} className="bg-gray-900 flex flex-col shrink-0">

          <div className="h-9 border-b border-gray-800 flex items-center px-3 shrink-0">
            <span className="text-xs font-semibold text-gray-400">채팅</span>
            <span className="ml-auto text-xs text-gray-700">{visibleMessages.length}</span>
          </div>

          {frozen && (
            <div className="bg-cyan-950/80 border-b border-cyan-800/40 px-3 py-2.5 flex items-center gap-2.5 shrink-0">
              <svg className="w-4 h-4 text-cyan-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <line x1="12" y1="2" x2="12" y2="22" />
                <line x1="2" y1="12" x2="22" y2="12" />
                <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
                <line x1="19.07" y1="4.93" x2="4.93" y2="19.07" />
                <circle cx="12" cy="12" r="2.5" fill="currentColor" stroke="none" />
              </svg>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-medium text-cyan-300">채팅 일시중지</p>
                <p className="text-[10px] text-cyan-600 leading-tight">관리자가 채팅을 중지했습니다</p>
              </div>
            </div>
          )}

          {visibleMessages.length === 0 && (
            <p className="text-center text-gray-700 text-xs py-6 shrink-0">첫 채팅을 보내보세요!</p>
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
            <div className="flex justify-center py-1 shrink-0">
              <button
                onClick={() => {
                  userPausedRef.current = false
                  virtuosoRef.current?.scrollToIndex({ index: 'LAST', behavior: 'smooth' })
                }}
                className="text-xs bg-indigo-600 hover:bg-indigo-500 text-white px-3 py-1 rounded-full transition-colors"
              >
                ↓ 최신
              </button>
            </div>
          )}

          <ChatInput onSend={sendMessage} disabled={!connected} frozen={frozen} errorMessage={wsError?.message} />
        </div>
      </div>
    </div>
  )
}

export default function ChatPage() {
  const { id } = useParams()
  const { user, token, logout, isAdmin } = useAuth()
  const navigate = useNavigate()
  const roomId = Number(id)

  if (!token) return <Navigate to="/" replace />
  if (!id || isNaN(roomId)) return <Navigate to="/home" replace />

  return (
    <ChatRoom
      roomId={roomId}
      user={user}
      token={token}
      logout={logout}
      navigate={navigate}
      isAdmin={isAdmin}
    />
  )
}
