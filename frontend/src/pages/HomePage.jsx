import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/auth-context'
import { getRooms } from '../api/axios'
import MainLayout from '../layouts/MainLayout'

const ROOM_THUMBNAILS = {
  1: 'https://chatguard-media-712789089571-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/room1.png',
  2: 'https://chatguard-media-712789089571-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/room2.png',
  3: 'https://chatguard-media-712789089571-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/room3.png',
}


function getThumbnail(roomId) {
  return ROOM_THUMBNAILS[roomId] ?? null
}

export default function HomePage() {
  const { token, isAdmin } = useAuth()
  const navigate  = useNavigate()
  const [rooms, setRooms]   = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState('')

  useEffect(() => {
    getRooms(token)
      .then(setRooms)
      .catch((err) => {
        setError(err.response?.data?.error?.message ?? err.response?.data?.message ?? '채팅방 목록을 불러오지 못했습니다.')
      })
      .finally(() => setLoading(false))
  }, [token])

  return (
    <MainLayout>
      <div className="max-w-6xl w-full mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-lg font-semibold text-white">라이브 채널</h2>
          {isAdmin && (
            <Link
              to="/admin"
              className="flex items-center gap-1.5 text-xs text-gray-400 hover:text-indigo-300 border border-gray-700 hover:border-indigo-600 px-3 py-1.5 rounded-lg transition-colors"
            >
              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              관리자
            </Link>
          )}
        </div>

        {loading && (
          <div className="flex justify-center py-12">
            <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
          </div>
        )}

        {error && (
          <div className="text-red-400 text-sm bg-red-400/10 px-4 py-3 rounded-xl">{error}</div>
        )}

        {!loading && !error && rooms.length === 0 && (
          <p className="text-gray-500 text-sm text-center py-12">개설된 채팅방이 없습니다.</p>
        )}

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {rooms.map((room) => {
            const thumbnail = getThumbnail(room.id)
            const viewers = room.presence_count ?? 0
            return (
              <button
                key={room.id}
                onClick={() => navigate(`/chat/${room.id}`)}
                className="text-left group cursor-pointer"
              >
                <div className="relative aspect-video bg-gray-900 rounded-xl overflow-hidden mb-3">
                  {thumbnail ? (
                    <img
                      src={thumbnail}
                      alt={room.name}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
                    />
                  ) : (
                    <div className="w-full h-full bg-black" />
                  )}
                  <div className="absolute top-2 left-2 flex items-center gap-1 bg-black/70 px-1.5 py-0.5 rounded text-xs text-white">
                    <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
                    {viewers.toLocaleString()}
                  </div>
                </div>
                <div>
                  <p className="text-white text-sm font-medium truncate group-hover:text-indigo-300 transition-colors">{room.name}</p>
                  <p className="text-gray-400 text-xs mt-0.5">{room.streamer_name}</p>
                </div>
              </button>
            )
          })}
        </div>
      </div>
    </MainLayout>
  )
}
