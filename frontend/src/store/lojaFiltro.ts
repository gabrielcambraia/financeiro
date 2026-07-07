import { create } from 'zustand'
import { format } from 'date-fns'

interface EstadoFiltro {
  mes: string
  contaId?: number
  definirMes: (mes: string) => void
  definirContaId: (id?: number) => void
}

export const useLojaFiltro = create<EstadoFiltro>(set => ({
  mes: format(new Date(), 'yyyy-MM'),
  contaId: undefined,
  definirMes: mes => set({ mes }),
  definirContaId: contaId => set({ contaId }),
}))
