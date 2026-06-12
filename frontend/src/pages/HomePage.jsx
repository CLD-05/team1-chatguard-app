import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/auth-context'
import { getRooms } from '../api/axios'
import MainLayout from '../layouts/MainLayout'

export default function HomePage() {
  const { token } = useAuth()
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
        <h2 className="text-lg font-semibold text-white mb-4">채팅방 목록</h2>

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
