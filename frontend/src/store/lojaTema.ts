import { create } from 'zustand'

export type Tema = 'claro' | 'escuro'

interface EstadoTema {
  tema: Tema
  alternarTema: () => void
}

const CHAVE_STORAGE = 'financeiro.tema'

export const aplicarClasseTema = (tema: Tema) => {
  document.documentElement.classList.toggle('dark', tema === 'escuro')
}

export const carregarTemaInicial = (): Tema => {
  const bruto = localStorage.getItem(CHAVE_STORAGE)
  return bruto === 'escuro' ? 'escuro' : 'claro'
}

export const useLojaTema = create<EstadoTema>(set => ({
  tema: carregarTemaInicial(),
  alternarTema: () =>
    set(estado => {
      const novoTema: Tema = estado.tema === 'claro' ? 'escuro' : 'claro'
      localStorage.setItem(CHAVE_STORAGE, novoTema)
      aplicarClasseTema(novoTema)
      return { tema: novoTema }
    }),
}))
