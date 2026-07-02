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
        {title ? (
          <span className="font-bold text-white">{title}</span>
        ) : (
          <img
            src="https://chatguard-media-712789089571-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/logo-name.PNG"
            alt="ChatGuard"
            className="h-6 object-contain"
          />
        )}
        <div className="flex-1" />
        {user && (
          <div className="flex items-center gap-3">
            <span className="text-xs text-gray-400">{user.display_name}</span>
            <button
              onClick={handleLogout}
              className="text-xs text-gray-500 hover:text-red-400 transition-colors cursor-pointer"
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
