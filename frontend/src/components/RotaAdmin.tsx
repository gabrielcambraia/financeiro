import { Navigate, Outlet } from 'react-router-dom'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

export default function RotaAdmin() {
  const admin = useLojaAutenticacao(s => s.sessao?.nivelAcesso === 'ADMIN')
  if (!admin) return <Navigate to="/" replace />
  return <Outlet />
}
