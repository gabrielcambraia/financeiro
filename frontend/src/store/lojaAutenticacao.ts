import { create } from 'zustand'

export type PapelUsuario = 'DONO' | 'MEMBRO'

interface SessaoAutenticacao {
  token: string
  usuarioId: number
  nome: string
  email: string
  espacoId: number
  papel: PapelUsuario
  precisaTrocarSenha: boolean
}

interface EstadoAutenticacao {
  sessao: SessaoAutenticacao | null
  definirSessao: (sessao: SessaoAutenticacao) => void
  limparSessao: () => void
}

// O access token vive só em memória (nunca em localStorage) — sobrevive
// enquanto a aba estiver aberta e é reidratado a cada carregamento via
// POST /auth/renovar, que usa o cookie httpOnly de refresh token.
export const useLojaAutenticacao = create<EstadoAutenticacao>(set => ({
  sessao: null,
  definirSessao: sessao => set({ sessao }),
  limparSessao: () => set({ sessao: null }),
}))
