import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/auth-context'
import { login } from '../api/axios'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)
  const { login: authLogin }    = useAuth()
  const navigate                = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()
    const name = username.trim()
    const pass = password.trim()
    if (!name || !pass) return

    setLoading(true)
    setError('')
    try {
      const { user, token } = await login(name, pass)
      authLogin(user, token)
      navigate('/home')
    } catch (err) {
      const status = err.response?.status
      if (status === 401) {
        setError('아이디 또는 비밀번호가 일치하지 않습니다.')
      } else {
        setError(err.response?.data?.error?.message ?? err.response?.data?.message ?? '로그인 실패. 다시 시도해주세요.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-900 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <img
            src="https://chatguard-media-712789089571-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/logo-login.png"
            alt="ChatGuard"
            className="w-40 h-40 mx-auto object-contain -mb-8"
          />
          <p className="text-gray-400 text-sm mt-1">실시간 AI 검열 채팅 플랫폼</p>
        </div>

        <form onSubmit={handleSubmit} className="bg-gray-800 rounded-2xl p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1.5">닉네임</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="닉네임을 입력하세요"
              maxLength={50}
              autoFocus
              className="w-full bg-gray-700 text-white placeholder-gray-500 rounded-xl px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-indigo-500 transition"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1.5">비밀번호</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호를 입력하세요"
              className="w-full bg-gray-700 text-white placeholder-gray-500 rounded-xl px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-indigo-500 transition"
            />
          </div>

          {error && (
            <p className="text-red-400 text-xs bg-red-400/10 px-3 py-2 rounded-lg">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading || !username.trim() || !password.trim()}
            className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium rounded-xl transition-colors text-sm cursor-pointer disabled:cursor-not-allowed"
          >
            {loading ? '입장 중...' : '입장하기'}
          </button>
        </form>
      </div>
    </div>
  )
}
