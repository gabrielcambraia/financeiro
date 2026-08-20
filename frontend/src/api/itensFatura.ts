import cliente from './cliente'
import type { ConversaoParaDebitoPayload, ItemFatura, Transacao } from '../types'

interface FiltrosItemFatura {
  cartaoId?: number
  contaId?: number
  month?: string
  centroCustoId?: number
}

interface PayloadCriarItem {
  cartaoId: number
  categoriaId?: number
  valor: number
  descricao?: string
  data: string
  totalParcelas?: number
  centroCustoId?: number | null
}

export const buscarItensFatura = (filtros: FiltrosItemFatura) =>
  cliente.get<ItemFatura[]>('/itens-fatura', { params: filtros }).then(r => r.data)

// Itens em aberto do ciclo de faturamento que fecha no mês informado (baseado
// no dia de fechamento do cartão) — usado pela tela do cartão para navegar
// mês a mês, distinto do filtro de mês corrido de buscarItensFatura.
export const buscarItensFaturaPorCiclo = (cartaoId: number, month?: string) =>
  cliente.get<ItemFatura[]>('/itens-fatura/ciclo', { params: { cartaoId, month } }).then(r => r.data)

export const criarItemFatura = (data: PayloadCriarItem) =>
  cliente.post<ItemFatura[]>('/itens-fatura', data).then(r => r.data)

export const atualizarItemFatura = (id: number, data: PayloadCriarItem) =>
  cliente.put<ItemFatura>(`/itens-fatura/${id}`, data).then(r => r.data)

export const excluirItemFatura = (id: number) =>
  cliente.delete(`/itens-fatura/${id}`)

export const cancelarItemFatura = (id: number) =>
  cliente.patch<ItemFatura>(`/itens-fatura/${id}/cancelar`).then(r => r.data)

export const converterItemParaDebito = (id: number, payload: ConversaoParaDebitoPayload) =>
  cliente.post<Transacao[]>(`/itens-fatura/${id}/converter-para-debito`, payload).then(r => r.data)
