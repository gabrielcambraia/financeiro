import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface EstadoEntidadeAtual {
  entidadeAtualId: number | null
  definirEntidadeAtual: (id: number | null) => void
}

export const useLojaEntidadeAtual = create<EstadoEntidadeAtual>()(
  persist(
    set => ({
      entidadeAtualId: null,
      definirEntidadeAtual: entidadeAtualId => set({ entidadeAtualId }),
    }),
    { name: 'entidadeAtualId' }
  )
)
