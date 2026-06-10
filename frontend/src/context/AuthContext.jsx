/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useCallback } from 'react'

const AuthContext = createContext(null)

const TOKEN_KEY = 'cg_token'
const USER_KEY  = 'cg_user'

function loadInitialState() {
  try {
    const token = sessionStorage.getItem(TOKEN_KEY)
    const raw   = sessionStorage.getItem(USER_KEY)
    if (token && raw) return { token, user: JSON.parse(raw) }
  } catch {
    // 손상된 스토리지 무시
  }
  return { token: null, user: null }
}

export function AuthProvider({ children }) {
  const [state, setState] = useState(loadInitialState)

  const login = useCallback((user, token) => {
    sessionStorage.setItem(TOKEN_KEY, token)
    sessionStorage.setItem(USER_KEY, JSON.stringify(user))
    setState({ user, token })
  }, [])

  const logout = useCallback(() => {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    setState({ user: null, token: null })
  }, [])

  return (
    <AuthContext.Provider value={{ ...state, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
