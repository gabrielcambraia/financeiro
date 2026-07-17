import cliente from './cliente'
import type { Transacao, TipoTransacao } from '../types'

interface FiltrosTransacao {
  month: string
  contaId?: number
  tipo?: TipoTransacao
  categoriaId?: number
}

interface PayloadCriarTransacao {
  contaId: number
  categoriaId?: number
  tipo: TipoTransacao
  tipoPagamento: 'DEBITO' | 'CREDITO'
  valor: number
  descricao?: string
  data: string
  dataVencimento?: string
  dataPagamento?: string
  quitarNaCriacao?: boolean
  fixa: boolean
  totalParcelas?: number
}

export const buscarTransacoes = (filtros: FiltrosTransacao) =>
  cliente.get<Transacao[]>('/transacoes', { params: filtros }).then(r => r.data)

export const criarTransacao = (data: PayloadCriarTransacao) =>
  cliente.post<Transacao[]>('/transacoes', data).then(r => r.data)

export const atualizarTransacao = (id: number, data: PayloadCriarTransacao) =>
  cliente.put<Transacao>(`/transacoes/${id}`, data).then(r => r.data)

export type EscopoExclusao = 'UNICA' | 'GRUPO' | 'FUTURAS'

export const excluirTransacao = (id: number, scope: EscopoExclusao = 'UNICA') =>
  cliente.delete(`/transacoes/${id}`, { params: { scope } })

export const pagarTransacao = (id: number, dataPagamento?: string) =>
  cliente.patch<Transacao>(`/transacoes/${id}/pagar`, dataPagamento ? { dataPagamento } : {}).then(r => r.data)

export const estornarTransacao = (id: number) =>
  cliente.patch<Transacao>(`/transacoes/${id}/estornar`).then(r => r.data)

export const cancelarTransacao = (id: number, scope: EscopoExclusao = 'UNICA') =>
  cliente.patch<Transacao>(`/transacoes/${id}/cancelar`, undefined, { params: { scope } }).then(r => r.data)
