import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from '../context/auth-context'
import LoginPage from '../pages/LoginPage'
import HomePage from '../pages/HomePage'
import ChatPage from '../pages/ChatPage'

function PrivateRoute({ children }) {
  const { token } = useAuth()
  return token ? children : <Navigate to="/" replace />
}

export default function Router() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route path="/home" element={<PrivateRoute><HomePage /></PrivateRoute>} />
      <Route path="/chat/:id" element={<PrivateRoute><ChatPage /></PrivateRoute>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
