import { Navigate, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { obterConfigAuth } from '../api/autenticacao'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

export default function RotaProtegida() {
  const sessao = useLojaAutenticacao(s => s.sessao)
  const { data: config, isLoading } = useQuery({
    queryKey: ['auth-config'],
    queryFn: obterConfigAuth,
    staleTime: Infinity,
  })

  if (isLoading) return null

  const requerAutenticacao = config?.requerAutenticacao ?? false
  if (requerAutenticacao && !sessao) {
    return <Navigate to="/login" replace />
  }

  if (requerAutenticacao && sessao?.precisaTrocarSenha) {
    return <Navigate to="/trocar-senha" replace />
  }

  return <Outlet />
}
