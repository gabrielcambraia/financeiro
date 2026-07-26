import cliente from './cliente'
import type { Fatura } from '../types'

export const buscarFaturas = (cartaoId: number) =>
  cliente.get<Fatura[]>('/faturas', { params: { cartaoId } }).then(r => r.data)

export const buscarFatura = (id: number) =>
  cliente.get<Fatura>(`/faturas/${id}`).then(r => r.data)
