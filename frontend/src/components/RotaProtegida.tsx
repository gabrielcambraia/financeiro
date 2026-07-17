import { Navigate, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { obterConfigAuth } from '../api/autenticacao'
import { renovarSessao } from '../api/cliente'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

export default function RotaProtegida() {
  const sessao = useLojaAutenticacao(s => s.sessao)
  const { data: config, isLoading: carregandoConfig } = useQuery({
    queryKey: ['auth-config'],
    queryFn: obterConfigAuth,
    staleTime: Infinity,
  })

  // Access token vive só em memória — a cada carregamento da SPA, tenta
  // reidratar a sessão a partir do cookie httpOnly de refresh token antes
  // de decidir se redireciona para /login.
  const { isLoading: carregandoSessao } = useQuery({
    queryKey: ['bootstrap-sessao'],
    queryFn: renovarSessao,
    enabled: !sessao,
    retry: false,
    staleTime: Infinity,
  })

  if (carregandoConfig || (!sessao && carregandoSessao)) return null

  const requerAutenticacao = config?.requerAutenticacao ?? false
  if (requerAutenticacao && !sessao) {
    return <Navigate to="/login" replace />
  }

  if (requerAutenticacao && sessao?.precisaTrocarSenha) {
    return <Navigate to="/trocar-senha" replace />
  }

  return <Outlet />
}
