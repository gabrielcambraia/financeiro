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

const CHAVE_STORAGE = 'financeiro.sessao'

const carregarSessaoInicial = (): SessaoAutenticacao | null => {
  const bruto = localStorage.getItem(CHAVE_STORAGE)
  if (!bruto) return null
  try {
    return JSON.parse(bruto) as SessaoAutenticacao
  } catch {
    return null
  }
}

export const useLojaAutenticacao = create<EstadoAutenticacao>(set => ({
  sessao: carregarSessaoInicial(),
  definirSessao: sessao => {
    localStorage.setItem(CHAVE_STORAGE, JSON.stringify(sessao))
    set({ sessao })
  },
  limparSessao: () => {
    localStorage.removeItem(CHAVE_STORAGE)
    set({ sessao: null })
  },
}))
