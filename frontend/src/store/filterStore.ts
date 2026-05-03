import { create } from 'zustand'
import { format } from 'date-fns'

interface FilterState {
  month: string
  accountId?: number
  setMonth: (month: string) => void
  setAccountId: (id?: number) => void
}

export const useFilterStore = create<FilterState>(set => ({
  month: format(new Date(), 'yyyy-MM'),
  accountId: undefined,
  setMonth: month => set({ month }),
  setAccountId: accountId => set({ accountId }),
}))
