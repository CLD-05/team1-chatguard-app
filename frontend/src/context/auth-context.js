import { createContext, useContext } from 'react'

// 컨텍스트 객체와 훅은 컴포넌트가 아니므로 별도 모듈에 둔다.
// (AuthContext.jsx가 컴포넌트만 export 해야 react-refresh/Fast Refresh가 정상 동작 — react-refresh/only-export-components)
export const AuthContext = createContext(null)

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
