import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/auth-context'

export default function MainLayout({ children, title, showBack }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/')
  }

  return (
    <div className="min-h-screen flex flex-col bg-gray-900">
      <header className="h-12 bg-gray-900 border-b border-gray-800 flex items-center px-4 gap-3 shrink-0">
        {showBack && (
          <button
            onClick={() => navigate('/home')}
            className="text-gray-400 hover:text-white text-sm transition-colors"
          >
            ← 목록
          </button>
        )}
        <span className="font-bold text-white">{title ?? 'ChatGuard'}</span>
        <div className="flex-1" />
        {user && (
          <div className="flex items-center gap-3">
            <span className="text-xs text-gray-400">{user.display_name}</span>
            <button
              onClick={handleLogout}
              className="text-xs text-gray-500 hover:text-red-400 transition-colors"
            >
              로그아웃
            </button>
          </div>
        )}
      </header>
      <main className="flex-1">{children}</main>
    </div>
  )
}
