import cliente from './cliente'
import type { ItemFatura } from '../types'

interface FiltrosItemFatura {
  cartaoId: number
  month?: string
}

interface PayloadCriarItem {
  cartaoId: number
  categoriaId?: number
  valor: number
  descricao?: string
  data: string
  totalParcelas?: number
}

export const buscarItensFatura = (filtros: FiltrosItemFatura) =>
  cliente.get<ItemFatura[]>('/itens-fatura', { params: filtros }).then(r => r.data)

export const criarItemFatura = (data: PayloadCriarItem) =>
  cliente.post<ItemFatura[]>('/itens-fatura', data).then(r => r.data)

export const atualizarItemFatura = (id: number, data: PayloadCriarItem) =>
  cliente.put<ItemFatura>(`/itens-fatura/${id}`, data).then(r => r.data)

export const excluirItemFatura = (id: number) =>
  cliente.delete(`/itens-fatura/${id}`)

export const cancelarItemFatura = (id: number) =>
  cliente.patch<ItemFatura>(`/itens-fatura/${id}/cancelar`).then(r => r.data)
