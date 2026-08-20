import cliente from './cliente'
import type { Recorrencia, TipoTransacao, TipoPagamento } from '../types'

interface FiltrosRecorrencia {
  ativa?: boolean
  tipo?: TipoTransacao
  tipoPagamento?: TipoPagamento
  contaId?: number
  cartaoId?: number
}

interface PayloadRecorrencia {
  tipo: TipoTransacao
  tipoPagamento: TipoPagamento
  contaId?: number | null
  cartaoId?: number | null
  categoriaId?: number | null
  centroCustoId?: number | null
  valor: number
  descricao?: string
  diaCompetencia: number
  diaVencimento?: number | null
  debitoAutomatico?: boolean
  ativa?: boolean
  dataInicio: string
  dataFim?: string | null
}

export const buscarRecorrencias = (filtros?: FiltrosRecorrencia) =>
  cliente.get<Recorrencia[]>('/recorrencias', { params: filtros }).then(r => r.data)

export const buscarRecorrencia = (id: number) =>
  cliente.get<Recorrencia>(`/recorrencias/${id}`).then(r => r.data)

export const criarRecorrencia = (data: PayloadRecorrencia) =>
  cliente.post<Recorrencia>('/recorrencias', data).then(r => r.data)

export const atualizarRecorrencia = (id: number, data: PayloadRecorrencia) =>
  cliente.put<Recorrencia>(`/recorrencias/${id}`, data).then(r => r.data)

export const toggleAtivaRecorrencia = (id: number, ativa: boolean) =>
  cliente.patch<Recorrencia>(`/recorrencias/${id}/ativa`, { ativa }).then(r => r.data)

export const excluirRecorrencia = (id: number) =>
  cliente.delete(`/recorrencias/${id}`)

export const gerarMesAtualRecorrencia = (id: number) =>
  cliente.post<Recorrencia>(`/recorrencias/${id}/gerar-mes-atual`).then(r => r.data)
