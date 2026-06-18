import { useCallback, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/auth-context'
import DashboardTab from '../components/admin/DashboardTab'
import KeywordsTab from '../components/admin/KeywordsTab'
import LogsTab from '../components/admin/LogsTab'

const NAV = [
  { id: 'dashboard', label: '대시보드',         icon: '▦' },
  { id: 'keywords',  label: '금칙어 관리',       icon: '🚫' },
  { id: 'logs',      label: '모더레이션 로그',   icon: '📋' },
]

const PAGE_TITLE = { dashboard: '대시보드', keywords: '금칙어 관리', logs: '모더레이션 로그' }

export default function AdminPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [tab, setTab] = useState('dashboard')
  const [forbidden, setForbidden] = useState(false)

  const guard = useCallback((promise) =>
    promise.catch((err) => {
      if (err.response?.status === 403) setForbidden(true)
      throw err
    }), [])

  function handleLogout() { logout(); navigate('/') }

  if (forbidden) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center flex-col gap-3">
        <p className="text-red-400 font-medium">접근 권한이 없습니다.</p>
        <Link to="/home" className="text-sm text-gray-400 hover:text-white">← 홈으로</Link>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-900 flex">
      {/* 사이드바 */}
      <aside className="w-52 shrink-0 bg-gray-900 border-r border-gray-800 flex flex-col">
        <div className="h-12 flex items-center px-4 border-b border-gray-800 gap-2">
          <Link to="/home" className="font-bold text-white text-sm hover:text-indigo-300 transition-colors">ChatGuard</Link>
          <span className="text-xs bg-indigo-900 text-indigo-300 border border-indigo-700 px-2 py-0.5 rounded-full">Admin</span>
        </div>

        <nav className="flex-1 py-2">
          <p className="text-xs text-gray-600 px-4 py-2 uppercase tracking-wider">메뉴</p>
          {NAV.map(({ id, label, icon }) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              className={`w-full flex items-center gap-2.5 px-4 py-2.5 text-sm transition-colors text-left ${
                tab === id ? 'text-white bg-gray-800' : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/50'
              }`}
            >
              <span className="text-base">{icon}</span>
              {label}
            </button>
          ))}
        </nav>

        <div className="p-4 border-t border-gray-800">
          <div className="flex items-center gap-2.5 mb-3">
            <div className="w-7 h-7 rounded-full bg-indigo-900 flex items-center justify-center text-xs text-indigo-300 font-medium shrink-0">
              {user?.display_name?.[0] ?? 'A'}
            </div>
            <div className="min-w-0">
              <p className="text-xs text-white truncate">{user?.display_name}</p>
              <p className="text-xs text-gray-500">관리자</p>
            </div>
          </div>
          <button onClick={handleLogout} className="text-xs text-gray-500 hover:text-red-400 transition-colors">
            로그아웃
          </button>
        </div>
      </aside>

      {/* 메인 */}
      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-12 border-b border-gray-800 flex items-center px-6 shrink-0">
          <span className="text-sm font-medium text-white">{PAGE_TITLE[tab]}</span>
        </header>

        <main className="flex-1 overflow-y-auto p-6">
          {tab === 'dashboard' && <DashboardTab guard={guard} />}
          {tab === 'keywords'  && <KeywordsTab  guard={guard} />}
          {tab === 'logs'      && <LogsTab      guard={guard} />}
        </main>
      </div>
    </div>
  )
}
