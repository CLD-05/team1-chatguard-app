import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/auth-context'
import { getRooms } from '../api/axios'
import MainLayout from '../layouts/MainLayout'

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
    <MainLayout title="ChatGuard">
      <div className="max-w-2xl w-full mx-auto px-4 py-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-white">채팅방 목록</h2>
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

        <div className="space-y-2">
          {rooms.map((room) => (
            <button
              key={room.id}
              onClick={() => navigate(`/chat/${room.id}`)}
              className="w-full flex items-center justify-between bg-gray-800 hover:bg-gray-750 border border-gray-700 hover:border-indigo-500 rounded-xl px-4 py-4 transition-colors group text-left"
            >
              <div>
                <p className="font-medium text-white group-hover:text-indigo-300 transition-colors">
                  {room.name}
                </p>
                <p className="text-sm text-gray-400 mt-0.5">스트리머: {room.streamer_name}</p>
              </div>
              <svg className="w-5 h-5 text-gray-500 group-hover:text-indigo-400 transition-colors"
                fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </button>
          ))}
        </div>
      </div>
    </MainLayout>
  )
}
