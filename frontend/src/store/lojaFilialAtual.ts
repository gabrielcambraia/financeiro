import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface EstadoFilialAtual {
  filialAtualId: number | null
  definirFilialAtual: (id: number | null) => void
}

export const useLojaFilialAtual = create<EstadoFilialAtual>()(
  persist(
    set => ({
      filialAtualId: null,
      definirFilialAtual: filialAtualId => set({ filialAtualId }),
    }),
    { name: 'filialAtualId' }
  )
)
